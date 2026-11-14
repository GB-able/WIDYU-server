package com.widyu.location.realtime.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.websocket.WebSocketWorkDispatcher;
import com.widyu.location.realtime.application.MemberLocationMailbox.Outcome;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationFreshness;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시니어 위치 수신의 조율자(LLD-0142, LLD-0143). 파싱, 검증, 회원별 순서 제어, ACK 결정을 맡는다.
 * 판정({@link LocationJudgeService})과 Redis 반영, 방송({@link LocationProjectionService})은 다른 빈이 한다.
 * 위치 원본은 더 저장하지 않는다(LLD-0143). 과거 {@code location_fix} 행은 남아 있고 내보내기가 그대로 읽는다.
 *
 * <p>처리 순서:
 * <pre>
 * receive ─ 파싱, 검증, 본인 ─ applyRealtime ─ 회원 잠금 ┬ 최신 비교
 *                                                        ├ judge (자기 트랜잭션, 반환 때 커밋)
 *                                                        └ project (최신 위치 필수, 나머지 실패 허용)
 *         └ 반영하지 않은 위치면 시니어 확인 ─ ACK 결정(APPLIED, SKIPPED, REJECTED)
 * </pre>
 *
 * <p>트랜잭션을 열지 않는다. 회원 잠금은 커넥션 없이 기다린다(LLD-0114). 이 클래스에서 트랜잭션 없는 JPA 조회를 하지 않는다.
 * {@code NOT_SUPPORTED} 범위에서 그런 조회가 묶은 EntityManager는 메서드가 끝날 때까지 커넥션을 쥔다(LLD-0139).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class LocationIngestService {

    private static final Set<String> REASONS = Set.of("move", "keepalive", "incident");
    private static final double MAX_LATITUDE = 90.0;
    private static final double MAX_LONGITUDE = 180.0;
    // 응급 이동 경로는 최근 15분만 보여 준다. 그보다 오래 전에 잰 위치는 실시간 경로에 반영하지 않는다(LLD-0114).
    private static final long REALTIME_MAX_AGE_MS = Duration.ofMinutes(15).toMillis();

    private final Validator validator;
    private final MemberLocationSequencer sequencer;
    private final LocationJudgeService judgeService;
    private final LocationProjectionService projectionService;
    private final MemberLocationMailbox mailbox;
    private final WebSocketWorkDispatcher workDispatcher;
    // 미지정 필드를 무시하고 페이로드를 읽는 전용 매퍼. 주입받지 않는 이유는 Redis·응답 직렬화와
    // 설정을 공유하면 안 되기 때문이다(초기화 필드라 생성자 주입 대상에서 빠진다).
    private final ObjectMapper payloadMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * 원문 바이트를 받아 실시간 경로를 돌린다. 검증은 브로드캐스트 앞에 둔다. 잘못된 좌표를 보호자에게 흘리지 않기 위해서다.
     *
     * <p>v2 위치의 ACK는 "서버가 이 위치의 처리를 끝냈다"는 뜻이다(LLD-0143). 보호자가 받았다는 뜻은 아니다.
     * <ul>
     *   <li>{@code APPLIED}: 판정을 커밋하고 Redis 최신 위치에 반영했다(경로, 방송은 실패해도 로그만 남긴다).</li>
     *   <li>{@code SKIPPED}: 정상 요청이지만 반영할 필요가 없다. 15분 넘게 지났거나, 최신 위치보다 먼저 잰 위치(같은 위치의
     *   재전송 포함)이거나, 더 늦게 잰 위치가 반영됐다.</li>
     *   <li>{@code REJECTED}: 요청 자체가 잘못됐다(입력, 본인 아님, 시니어 아님). 다시 보내도 같다.</li>
     * </ul>
     * 판정, 최신 위치 저장이 기술 오류(DB, Redis)로 실패하면 ACK 없이 오류를 보내 재전송을 받는다. 같은 회원 위치가 너무
     * 몰려 차례를 얻지 못하면 경고만 남기고 ACK 없이 {@code null}을 돌려준다(컨트롤러가 아무것도 보내지 않는다).
     */
    public LocationAckResponse receive(byte[] payload, Long authenticatedMemberId) {
        return process(prepare(payload, authenticatedMemberId));
    }

    /**
     * STOMP 수신 스레드의 진입점(LLD-0144). 파싱, 검증, 본인 확인만 하고 회원 우편함에 넣은 뒤 바로 돌아간다. 실시간 처리와
     * ACK는 위치 실행기가 원래 발신 세션에 보낸다. 입력 오류는 여기서 바로 {@code REJECTED}를 돌려준다(컨트롤러가 보낸다).
     * 그 밖에는 {@code null}을 돌려준다. 구형 위치의 입력 오류는 예전처럼 예외로 알린다.
     */
    public LocationAckResponse submit(byte[] payload, Long authenticatedMemberId, Principal user, String sessionId) {
        Prepared prepared = prepare(payload, authenticatedMemberId);
        if (prepared.rejection() != null) {
            return prepared.rejection();
        }
        mailbox.submit(authenticatedMemberId, new LocationJob(prepared, user, sessionId, MDC.getCopyOfContextMap()));
        return null;
    }

    /**
     * 수신 때 할 일. 비교 기준 시각(순서 시각)은 여기서 한 번 정하고 우편함 비교와 실시간 처리가 같은 값을 쓴다(LLD-0140).
     * DB, Redis를 부르지 않는다.
     */
    Prepared prepare(byte[] payload, Long authenticatedMemberId) {
        long serverReceivedAtMs = System.currentTimeMillis();
        LocationUpdateRequest request = parse(payload);
        Long orderAtMs = orderAt(request.tsMs(), serverReceivedAtMs);

        if (!request.isV2()) {
            validate(request);
            verifySelf(request.memberId(), authenticatedMemberId);
            return new Prepared(request, authenticatedMemberId, serverReceivedAtMs, orderAtMs, null);
        }
        try {
            // 잘못된 요청, 남의 위치는 실시간 부수효과(방송, 안전구역 판정)가 생기기 전에 거절한다.
            validate(request);
            validateFix(request);
            verifySelf(request.memberId(), authenticatedMemberId);
        } catch (BusinessException e) {
            return new Prepared(request, authenticatedMemberId, serverReceivedAtMs, orderAtMs,
                    rejected(request, authenticatedMemberId, e));
        }
        return new Prepared(request, authenticatedMemberId, serverReceivedAtMs, orderAtMs, null);
    }

    /** 작업 스레드에서 할 일. 회원 순서 잠금 아래 실시간 처리를 하고 ACK를 정한다. 기술 오류는 던진다(ACK 없음). */
    LocationAckResponse process(Prepared prepared) {
        if (prepared.rejection() != null) {
            return prepared.rejection();
        }
        LocationUpdateRequest request = prepared.request();
        Long authenticatedMemberId = prepared.memberId();
        if (!request.isV2()) {
            return LocationAckResponse.legacy(applyRealtime(request, authenticatedMemberId, prepared.receivedAtMs()));
        }

        LocationUpdateResponse response;
        try {
            response = applyRealtime(request, authenticatedMemberId, prepared.receivedAtMs());
            if (!isApplied(response)) {
                // 반영하지 않은 위치는 판정(시니어 확인)을 거치지 않았다. 시니어가 아니면 생략이 아니라 거절이다.
                judgeService.requireSenior(request.memberId());
            }
        } catch (BusinessException e) {
            return rejected(request, authenticatedMemberId, e);
        } catch (LocationSequenceBusyException e) {
            log.warn("위치 실시간 처리 대기 초과로 ACK 보류: memberId={}, seq={}", authenticatedMemberId, request.seq());
            return null;
        } catch (RuntimeException e) {
            log.warn("위치 실시간 처리 실패로 ACK 보류: memberId={}, seq={}, cause={}",
                    authenticatedMemberId, request.seq(), e.getClass().getSimpleName());
            throw e;
        }
        if (isApplied(response)) {
            return LocationAckResponse.of(response, request, LocationAckResult.APPLIED);
        }
        return LocationAckResponse.of(response, request, LocationAckResult.SKIPPED);
    }

    /** 이미 파싱, 검증한 위치의 실시간 처리. {@link #receive}의 구형 위치와 v2 위치가 같은 경로를 탄다. */
    public LocationUpdateResponse applyRealtime(LocationUpdateRequest request, Long authenticatedMemberId) {
        return applyRealtime(request, authenticatedMemberId, System.currentTimeMillis());
    }

    /**
     * 실시간 반영 대상이면 반영하고, 아니면 받은 좌표만 ACK로 돌려준다. 최신 판단부터 판정 트랜잭션, 최신 위치 저장, 방송
     * 요청까지 같은 회원 안에서 한 번에 하나씩 실행한다(LLD-0139). 판정 트랜잭션이 끝나도 잠금은 최신 위치를 저장할 때까지
     * 쥔다. 판정 뒤 잠금을 풀면 뒤에 온 위치가 먼저 저장되고 앞선 위치가 그것을 덮는다. 15분 넘게 지난 위치는 최신 위치를
     * 읽지 않고 바로 거르므로 차례를 기다리지 않는다.
     */
    private LocationUpdateResponse applyRealtime(LocationUpdateRequest request, Long authenticatedMemberId,
                                                 long serverReceivedAtMs) {
        verifySelf(request.memberId(), authenticatedMemberId);
        if (isTooOld(request)) {
            return skipped(request, serverReceivedAtMs);
        }
        // 순서 판정, 최신값 비교, Redis 저장이 모두 이 한 값을 쓴다(LLD-0140). 시점마다 따로 "지금"으로 자르면 미래로 잰
        // 위치가 처리 중일 때 뒤에 온 정상 위치를 밀린 것으로 보거나 최신보다 먼저 잰 것으로 보고 반영 없이 ACK한다.
        Long orderAtMs = orderAt(request.tsMs(), serverReceivedAtMs);
        return sequencer.run(request.memberId(), orderAtMs, () -> {
            // 차례를 기다리는 동안 15분을 넘길 수 있어 잠금 안에서 다시 본다.
            if (isTooOld(request) || !isNewerThanLatest(request.memberId(), orderAtMs)) {
                return skipped(request, serverReceivedAtMs);
            }
            LocationUpdateResponse response = judgeService.judge(request, serverReceivedAtMs);
            projectionService.project(request, response, orderAtMs, serverReceivedAtMs);
            return response;
        }, () -> superseded(request, serverReceivedAtMs));
    }

    /** 이 경로의 업무 예외는 요청 자체의 문제(좌표, 필드 검증, 본인 아님, 시니어 아님)라 다시 보내도 같다. */
    private LocationAckResponse rejected(LocationUpdateRequest request, Long authenticatedMemberId, BusinessException e) {
        log.warn("위치 거절: memberId={}, seq={}, code={}",
                authenticatedMemberId, request.seq(), e.getErrorCode().getCode());
        return LocationAckResponse.rejected(request);
    }

    /**
     * 같은 회원의 더 늦게 잰 위치가 실시간에 반영돼 이 위치의 실시간 처리는 할 필요가 없다(LLD-0140). {@code SKIPPED}로
     * ACK한다. 더 최근 위치가 실패하면 순서 잠금이 과부하 예외를 던져 이 위치도 ACK를 받지 못한다.
     */
    private LocationUpdateResponse superseded(LocationUpdateRequest request, long serverReceivedAtMs) {
        log.info("위치 실시간 반영 생략(더 최근 위치 반영됨): memberId={}, seq={}", request.memberId(), request.seq());
        return requestOnly(request, serverReceivedAtMs);
    }

    private LocationUpdateResponse skipped(LocationUpdateRequest request, long serverReceivedAtMs) {
        log.info("위치 실시간 반영 생략: memberId={}, seq={}", request.memberId(), request.seq());
        return requestOnly(request, serverReceivedAtMs);
    }

    /** 실시간에 반영하지 않은 위치의 ACK. 이 요청의 좌표와 측정 시각만 담고 출처는 {@code REQUEST}다. */
    private static LocationUpdateResponse requestOnly(LocationUpdateRequest request, long serverReceivedAtMs) {
        return LocationUpdateResponse.received(request.memberId(), request.resolvedLatitude(),
                request.resolvedLongitude(), LocationFreshness.of(request.tsMs(), serverReceivedAtMs,
                        LocationSource.REQUEST, System.currentTimeMillis()));
    }

    /**
     * 15분 안에 잰 위치이면서 지금 최신 위치보다 나중에 잰 것만 실시간에 반영한다. 재연결 직후 밀려 온
     * 옛 위치가 현재 위치처럼 방송되거나 안전구역 판정을 다시 돌리지 않게 한다. 잰 시각(ts_ms)이 없는
     * 옛 형식은 비교할 수 없어 그대로 반영한다(LLD-0114).
     */
    private boolean isTooOld(LocationUpdateRequest request) {
        Long measuredAtMs = request.tsMs();
        return measuredAtMs != null && System.currentTimeMillis() - measuredAtMs > REALTIME_MAX_AGE_MS;
    }

    private boolean isNewerThanLatest(Long memberId, Long orderAtMs) {
        if (orderAtMs == null) {
            return true;
        }
        Long latestOrderAtMs = projectionService.latestOrderAt(memberId);
        if (latestOrderAtMs == null) {
            return true;
        }
        return orderAtMs > latestOrderAtMs;
    }

    /**
     * 최신 위치의 비교 기준(순서 시각). 미래 시각은 서버가 받은 시각으로 자른다. 기기 시계가 앞서 미래로 잰 위치가 기준이
     * 되면 그 뒤 정상 시각의 위치가 모두 "최신보다 먼저 잰 위치"로 빠지고, TTL 갱신이 그 값을 계속 살려 둔다.
     * 요청마다 한 번만 계산해 같은 값을 쓴다.
     */
    private static Long orderAt(Long measuredAtMs, long serverReceivedAtMs) {
        if (measuredAtMs == null) {
            return null;
        }
        return Math.min(measuredAtMs, serverReceivedAtMs);
    }

    private void verifySelf(Long memberId, Long authenticatedMemberId) {
        if (!memberId.equals(authenticatedMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 위치만 업데이트할 수 있습니다.");
        }
    }

    private LocationUpdateRequest parse(byte[] payload) {
        try {
            return payloadMapper.readValue(payload, LocationUpdateRequest.class);
        } catch (IOException e) {
            // 예외 메시지에 좌표가 섞일 수 있어 어떤 레벨에도 남기지 않는다(LLD-0029).
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
    }

    /** 판정이 커밋되고 최신 위치에 반영됐는지. 반영하지 않은 위치의 응답은 출처가 {@code REQUEST}다. */
    private static boolean isApplied(LocationUpdateResponse response) {
        return response.locationSource() == LocationSource.LATEST;
    }

    /**
     * v2 위치 검증(LLD-0048 6절의 입력 규칙). {@code device_id}, {@code session_id}, {@code seq}는 ACK가 요청을 가리키는
     * 식별자라 함께 본다. 예전에는 기기나 잰 시각이 빠진 v2를 구형으로 처리했지만, 이제 v2를 선언하면 입력 오류로 거절한다(LLD-0143).
     */
    private void validateFix(LocationUpdateRequest request) {
        if (request.deviceId() == null || request.tsMs() == null || request.sessionId() == null
                || request.seq() == null) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
        // Set.of는 null을 물으면 NPE를 던진다. 빠진 reason도 입력 오류로 거절한다.
        if (request.reason() == null || !REASONS.contains(request.reason())) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
    }

    private void validate(LocationUpdateRequest request) {
        Set<ConstraintViolation<LocationUpdateRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
        // 좌표는 lat/lon과 latitude/longitude 중 실제로 쓸 값이 정해진 뒤에 본다.
        if (request.resolvedLatitude() == null || request.resolvedLongitude() == null) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
        // 범위 검사는 구형과 v2 모두 방송 앞에서 한다. 범위 밖 좌표가 최신 위치로 반영되고 방송되지 않게 한다.
        if (Math.abs(request.resolvedLatitude()) > MAX_LATITUDE || Math.abs(request.resolvedLongitude()) > MAX_LONGITUDE) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
    }

    /** 수신 스레드에서 정한 요청. {@code rejection}이 있으면 입력 오류라 실시간 처리 없이 그 응답으로 끝난다. */
    record Prepared(LocationUpdateRequest request, Long memberId, long receivedAtMs, Long orderAtMs,
                    LocationAckResponse rejection) {
    }

    /**
     * 우편함 작업 하나(LLD-0144). 수신 스레드의 발신 세션, 인증 정보, MDC를 들고 위치 실행기에서 돈다. 응답은 원래 발신
     * 세션에만 보낸다. 연결이 끊긴 세션으로 보낸 응답은 브로커가 버린다(처리 자체는 끝까지 한다).
     */
    private final class LocationJob implements MemberLocationMailbox.Job {

        private static final String API = "RealtimeLocationController.updateLocation";
        private static final String DESTINATION = "/app/location/update";
        private static final String ACK_DESTINATION = "/queue/location/ack";

        private final Prepared prepared;
        private final Principal user;
        private final String sessionId;
        private final Map<String, String> mdc;

        private LocationJob(Prepared prepared, Principal user, String sessionId, Map<String, String> mdc) {
            this.prepared = prepared;
            this.user = user;
            this.sessionId = sessionId;
            this.mdc = mdc;
        }

        @Override
        public Long orderAtMs() {
            return prepared.orderAtMs();
        }

        @Override
        public Outcome run() {
            Outcome[] outcome = {Outcome.FAILED};
            withMdc(() -> outcome[0] = runAndReply());
            return outcome[0];
        }

        private Outcome runAndReply() {
            try {
                LocationAckResponse ack = process(prepared);
                if (ack == null) {
                    // 회원 순서 잠금 대기 초과(동기 경로와 겹칠 때). 성공 ACK 없이 과부하를 알려 재전송을 받는다.
                    workDispatcher.sendOverloaded(API, user, sessionId, requestIds());
                    return Outcome.FAILED;
                }
                send(ack);
                return outcomeOf(ack);
            } catch (RuntimeException e) {
                workDispatcher.sendError(e, API, DESTINATION, user, sessionId);
                return Outcome.FAILED;
            }
        }

        /** 같거나 늦게 잰 위치가 반영되거나 생략으로 끝났으니 이 위치는 실시간에 반영할 필요가 없다. 같은 회원이라 거절도 같다. */
        @Override
        public void settledBy(Outcome outcome) {
            withMdc(() -> settle(outcome));
        }

        @Override
        public void overloaded() {
            withMdc(() -> {
                log.warn("위치 실시간 처리 대기 공간 부족으로 ACK 보류: memberId={}, seq={}",
                        prepared.memberId(), prepared.request().seq());
                workDispatcher.sendOverloaded(API, user, sessionId, requestIds());
            });
        }

        private void settle(Outcome outcome) {
            LocationUpdateRequest request = prepared.request();
            if (outcome == Outcome.REJECTED) {
                if (request.isV2()) {
                    send(LocationAckResponse.rejected(request));
                    return;
                }
                workDispatcher.sendError(new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."),
                        API, DESTINATION, user, sessionId);
                return;
            }
            log.info("위치 실시간 반영 생략(더 최근 위치 반영됨): memberId={}, seq={}", prepared.memberId(), request.seq());
            LocationUpdateResponse response = requestOnly(request, prepared.receivedAtMs());
            if (request.isV2()) {
                send(LocationAckResponse.of(response, request, LocationAckResult.SKIPPED));
                return;
            }
            send(LocationAckResponse.legacy(response));
        }

        /** 과부하 오류에 실어 앱이 어느 위치가 처리되지 않았는지 찾게 한다. 구형 위치는 식별자가 없어 null이다. */
        private Map<String, Object> requestIds() {
            LocationUpdateRequest request = prepared.request();
            Map<String, Object> ids = new java.util.LinkedHashMap<>();
            ids.put("deviceId", request.deviceId());
            ids.put("sessionId", request.sessionId());
            ids.put("seq", request.seq());
            return ids;
        }

        private void send(LocationAckResponse ack) {
            workDispatcher.sendToSession(user.getName(), sessionId, ACK_DESTINATION, ack);
        }

        /** 수신 때의 MDC로 실행하고 이 스레드의 원래 MDC로 되돌린다(과부하 응답은 수신 스레드에서 불릴 수 있다). */
        private void withMdc(Runnable action) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                action.run();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        }
    }

    /** 구형 ACK는 결과가 없어 출처로 가른다. 반영했으면 최신 위치(LATEST), 아니면 요청 좌표(REQUEST)다. */
    private static Outcome outcomeOf(LocationAckResponse ack) {
        if (ack.result() == LocationAckResult.REJECTED) {
            return Outcome.REJECTED;
        }
        if (ack.result() == LocationAckResult.APPLIED) {
            return Outcome.APPLIED;
        }
        if (ack.result() == LocationAckResult.SKIPPED) {
            return Outcome.SKIPPED;
        }
        if (isApplied(ack.location())) {
            return Outcome.APPLIED;
        }
        return Outcome.SKIPPED;
    }
}
