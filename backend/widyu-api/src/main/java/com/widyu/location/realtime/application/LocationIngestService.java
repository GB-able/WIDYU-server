package com.widyu.location.realtime.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationFreshness;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        long serverReceivedAtMs = System.currentTimeMillis();
        LocationUpdateRequest request = parse(payload);

        if (!request.isRawFix()) {
            validate(request);
            return LocationAckResponse.legacy(applyRealtime(request, authenticatedMemberId, serverReceivedAtMs));
        }

        LocationUpdateResponse response;
        try {
            // 잘못된 요청, 남의 위치는 실시간 부수효과(방송, 안전구역 판정)가 생기기 전에 거절한다.
            validate(request);
            validateFix(request);
            response = applyRealtime(request, authenticatedMemberId, serverReceivedAtMs);
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
     * v2 위치 검증(LLD-0048 6절의 입력 규칙 그대로). {@code session_id}, {@code seq}는 ACK가 요청을 가리키는 식별자라
     * 함께 본다. 원본을 저장하지 않게 된 뒤에도 같은 입력을 받는다(LLD-0143).
     */
    private void validateFix(LocationUpdateRequest request) {
        if (request.tsMs() == null || request.sessionId() == null || request.seq() == null) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
        // Set.of는 null을 물으면 NPE를 던진다. 빠진 reason도 입력 오류로 거절한다.
        if (request.reason() == null || !REASONS.contains(request.reason())) {
            throw new BusinessException(ErrorCode.LOCATION_FIX_INVALID);
        }
        if (Math.abs(request.resolvedLatitude()) > MAX_LATITUDE
                || Math.abs(request.resolvedLongitude()) > MAX_LONGITUDE) {
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
    }
}
