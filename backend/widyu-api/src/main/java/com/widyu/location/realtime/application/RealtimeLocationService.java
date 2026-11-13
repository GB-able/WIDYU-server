package com.widyu.location.realtime.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.GeoUtils;
import com.widyu.location.SeniorLocation;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.raw.application.LocationFixService;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationFreshness;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.application.SeniorStayStateService;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RealtimeLocationService {

    private final SeniorLocationRepository seniorLocationRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final ParentLocationRepository parentLocationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final SafeZoneAlertService safeZoneAlertService;
    private final LocationFixService locationFixService;
    private final LocationAccessLogService locationAccessLogService;
    private final MemberRepository memberRepository;
    private final Validator validator;
    private final PlatformTransactionManager transactionManager;
    private final MemberLocationSequencer sequencer;
    private final SeniorStayStateService stayStateService;
    // 미지정 필드를 무시하고 페이로드를 읽는 전용 매퍼. 주입받지 않는 이유는 Redis·응답 직렬화와
    // 설정을 공유하면 안 되기 때문이다(초기화 필드라 생성자 주입 대상에서 빠진다).
    private final ObjectMapper payloadMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String LOCATION_TRAIL_KEY_PREFIX = "location:trail:";
    // @RedisHash 저장소가 쓰는 키 모양(keyspace:id)과 같아야 만료 연장이 그 위치에 걸린다.
    private static final String SENIOR_LOCATION_KEY_PREFIX = SeniorLocation.KEYSPACE + ":";
    // 체류 판정 상태는 MySQL(senior_stay_state)이 권위 있는 저장소다(LLD-0141). 이 키는 예전 값을 지울 때만 쓴다.
    private static final String LEGACY_STAY_KEY_PREFIX = "location:stay:";
    private static final long TRAIL_TTL_SECONDS = 900; // 15분
    // 이동 경로 점 상한. 초당 1건으로 보내도 15분을 담는다. 위치마다 TTL을 다시 걸어 키가 만료되지 않으므로
    // 자르지 않으면 리스트가 끝없이 커진다(LLD-0098).
    private static final long TRAIL_MAX_POINTS = 900;
    private static final double STAY_RADIUS_METERS = 30.0; // 30m 이내면 같은 위치
    private static final double SAFE_ZONE_RADIUS_METERS = 75.0; // 안전구역 반경 75m (지름 150m)
    // 응급 이동 경로는 최근 15분만 보여 준다. 그보다 오래 전에 잰 위치는 실시간 경로에 반영하지 않는다(LLD-0114).
    private static final long REALTIME_MAX_AGE_MS = Duration.ofMinutes(15).toMillis();
    // 이동 경로로 돌려주는 기간. 측정 시각(순서 시각) 기준이다(LLD-0141).
    private static final long TRAIL_WINDOW_MS = Duration.ofMinutes(15).toMillis();

    /**
     * 원문 바이트를 받아 실시간 경로를 돌리고, v2 페이로드면 원본을 남긴다(LLD-0048 5절).
     * 검증은 브로드캐스트 앞에 둔다 — 잘못된 좌표를 보호자에게 흘리지 않기 위해서다.
     *
     * <p>실시간 경로와 원본 저장은 각자 트랜잭션에서 차례로 끝낸다. 실시간 트랜잭션이 커넥션을 쥔 채
     * 원본 저장(REQUIRES_NEW)이 커넥션을 하나 더 요청하면, 재연결 직후 위치가 몰릴 때 풀 전체가
     * 두 번째 커넥션을 기다리며 막힌다(LLD-0114). 그래서 이 메서드는 트랜잭션을 열지 않는다.
     *
     * <p>v2 위치의 ACK는 "앱이 큐에서 지워도 된다"는 뜻이다(LLD-0123, LLD-0139). 원본이 저장됐고(또는 이미 있었고)
     * 실시간 처리가 끝났거나 필요 없을 때만 {@code STORED}, {@code DUPLICATE}를, 요청 자체가 잘못됐으면 {@code REJECTED}를
     * 돌려준다. 원본 저장이 실패하면 예외를 그대로 던져 ACK를 보내지 않는다.
     *
     * <p>실시간 처리가 기술 오류(Redis, DB 장애)로 실패해도 원본 저장은 시도한다(LLD-0139). 저장한 뒤 실시간 오류를
     * 던져 ACK를 보내지 않으므로 앱이 같은 위치를 다시 보내고, 그때 원본은 {@code DUPLICATE}이며 실시간 처리는
     * 최신 시각 비교로 다시 평가된다. 같은 회원 위치가 너무 몰려 실시간 차례를 얻지 못하면 경고만 남기고 ACK 없이
     * {@code null}을 돌려준다(컨트롤러가 아무것도 보내지 않는다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LocationAckResponse updateAndBroadcast(byte[] payload, Long authenticatedMemberId) {
        long serverReceivedAtMs = System.currentTimeMillis();
        LocationUpdateRequest request = parse(payload);

        if (!request.isRawFix()) {
            validate(request);
            return LocationAckResponse.legacy(applyRealtime(request, authenticatedMemberId, serverReceivedAtMs));
        }

        long acceptedAtMs;
        try {
            // 잘못된 요청, 남의 위치, 남의 회차는 실시간 부수효과(방송, 안전구역 판정)가 생기기 전에 거절한다.
            validate(request);
            acceptedAtMs = System.currentTimeMillis();
            locationFixService.validate(request);
            verifySelf(request.memberId(), authenticatedMemberId);
            locationFixService.verifyAttribution(authenticatedMemberId, request);
        } catch (BusinessException e) {
            return rejected(request, authenticatedMemberId, e);
        }

        LocationUpdateResponse response = null;
        RuntimeException realtimeFailure = null;
        try {
            response = applyRealtime(request, authenticatedMemberId, serverReceivedAtMs);
        } catch (BusinessException e) {
            // 실시간 경로의 업무 예외(시니어 아님)는 부수효과 전에 난다. 다시 보내도 같으므로 원본도 남기지 않는다.
            return rejected(request, authenticatedMemberId, e);
        } catch (RuntimeException e) {
            realtimeFailure = e;
        }

        boolean stored;
        try {
            stored = storeRaw(authenticatedMemberId, request, payload, serverReceivedAtMs, acceptedAtMs);
        } catch (BusinessException e) {
            return rejected(request, authenticatedMemberId, e);
        }

        if (realtimeFailure instanceof LocationSequenceBusyException) {
            log.warn("위치 원본 저장, 실시간 처리 대기 초과로 ACK 보류: memberId={}, seq={}",
                    authenticatedMemberId, request.seq());
            return null;
        }
        if (realtimeFailure != null) {
            log.warn("위치 원본 저장, 실시간 처리 실패로 ACK 보류: memberId={}, seq={}, cause={}",
                    authenticatedMemberId, request.seq(), realtimeFailure.getClass().getSimpleName());
            throw realtimeFailure;
        }
        if (stored) {
            return LocationAckResponse.of(response, request, LocationAckResult.STORED);
        }
        return LocationAckResponse.of(response, request, LocationAckResult.DUPLICATE);
    }

    /** 이 경로의 업무 예외는 요청 자체의 문제(좌표, 필드 검증, 본인 아님, 시니어 아님, 남의 회차)라 다시 보내도 같다. */
    private LocationAckResponse rejected(LocationUpdateRequest request, Long authenticatedMemberId, BusinessException e) {
        log.warn("위치 거절: memberId={}, seq={}, code={}",
                authenticatedMemberId, request.seq(), e.getErrorCode().getCode());
        return LocationAckResponse.rejected(request);
    }

    /**
     * 같은 원본이 거의 동시에 두 번 오면 둘 다 존재 확인을 통과하고 한쪽이 유일 키에서 실패한다. 그 행이 이제 있으면
     * 이미 저장된 위치이므로 {@code DUPLICATE}로 본다. 없으면 다른 무결성 오류라 그대로 던진다.
     */
    private boolean storeRaw(Long memberId, LocationUpdateRequest request, byte[] payload,
                             long serverReceivedAtMs, long acceptedAtMs) {
        try {
            return locationFixService.store(memberId, request, payload, serverReceivedAtMs, acceptedAtMs);
        } catch (DataIntegrityViolationException e) {
            if (locationFixService.exists(request)) {
                log.info("위치 원본 동시 중복 수신: memberId={}, seq={}", memberId, request.seq());
                return false;
            }
            throw e;
        }
    }

    /**
     * 실시간 반영 대상이면 반영하고, 아니면 받은 좌표만 ACK로 돌려준다. 최신 판단부터 방송 요청까지 같은 회원 안에서
     * 한 번에 하나씩 실행한다(LLD-0139). 15분 넘게 지난 위치는 최신 위치를 읽지 않고 바로 거르므로 차례를 기다리지 않는다.
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
            return updateAndBroadcast(request, authenticatedMemberId, orderAtMs, serverReceivedAtMs);
        }, () -> superseded(request, serverReceivedAtMs));
    }

    /**
     * 같은 회원의 더 늦게 잰 위치가 실시간에 반영돼 이 위치의 실시간 처리는 할 필요가 없다(LLD-0140). 원본 저장 결과로
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

    /** 실시간에 반영하지 않은 위치의 ACK. 이 요청의 좌표와 측정 시각만 담는다. */
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

    private boolean isNewerThanLatest(Long memberId, Long measuredAtMs) {
        if (measuredAtMs == null) {
            return true;
        }
        Long latestMeasuredAtMs = seniorLocationRepository.findById(memberId)
                .map(SeniorLocation::getMeasuredAtMs)
                .orElse(null);
        if (latestMeasuredAtMs == null) {
            return true;
        }
        return measuredAtMs > latestMeasuredAtMs;
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

    /**
     * 위치 한 건을 실시간에 반영한다. 안전구역 판정과 알림 예약은 DB 트랜잭션에서 확정하고, 최신 위치·이동 경로·방송·
     * 위치 이벤트는 그 트랜잭션이 끝나 커넥션을 돌려준 뒤에 처리한다(LLD-0121).
     *
     * <p>트랜잭션 안에서 Redis 쓰기를 기다리면 Redis가 느릴 때 그동안 커넥션을 쥐어, 위치가 몰리면 같은 풀을 쓰는
     * 알림 발송·스케줄러까지 막힌다. 또 롤백된 위치가 방송·위치 이벤트로 먼저 새어 나간다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LocationUpdateResponse updateAndBroadcast(LocationUpdateRequest request,
                                                      Long authenticatedMemberId) {
        long receivedAtMs = System.currentTimeMillis();
        return updateAndBroadcast(request, authenticatedMemberId, orderAt(request.tsMs(), receivedAtMs), receivedAtMs);
    }

    private LocationUpdateResponse updateAndBroadcast(LocationUpdateRequest request, Long authenticatedMemberId,
                                                      Long orderAtMs, long receivedAtMs) {
        LocationUpdateResponse response = new TransactionTemplate(transactionManager)
                .execute(status -> decideInTransaction(request, authenticatedMemberId, receivedAtMs));
        publishAfterTransaction(request, response, orderAtMs, receivedAtMs);
        return response;
    }

    /** DB 트랜잭션 안에서 할 일: 프로필 조회, 체류, 안전구역 판정과 상태 저장(이탈 시 알림 예약), 응답 생성. */
    private LocationUpdateResponse decideInTransaction(LocationUpdateRequest request, Long authenticatedMemberId,
                                                       long receivedAtMs) {
        Long memberId = request.memberId();
        verifySelf(memberId, authenticatedMemberId);

        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));
        Member seniorMember = seniorProfile.getMember();

        StayInfo stayInfo = calculateStayInfo(request.resolvedLatitude(), request.resolvedLongitude(), seniorMember);

        return LocationUpdateResponse.of(
                memberId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                request.resolvedLatitude(),
                request.resolvedLongitude(),
                stayInfo.startTime(),
                stayInfo.locationType(),
                stayInfo.locationName(),
                LocationFreshness.of(request.tsMs(), receivedAtMs, LocationSource.LATEST, System.currentTimeMillis())
        );
    }

    /**
     * 트랜잭션이 끝난 뒤 할 일. 최신 위치 저장이 실패하면 오류로 알린다. 저장되지 않았으니 같은 측정 시각의 재전송이
     * 실시간 후보로 다시 평가돼 복구된다. 그 뒤 단계는 최신 위치가 이미 저장돼 같은 시각의 재전송이 걸러지므로,
     * 오류를 돌려줘도 복구되지 않는다. 그래서 실패를 남기고 나머지를 계속한다.
     */
    private void publishAfterTransaction(LocationUpdateRequest request, LocationUpdateResponse response,
                                         Long orderAtMs, long receivedAtMs) {
        Long memberId = request.memberId();
        Double latitude = request.resolvedLatitude();
        Double longitude = request.resolvedLongitude();
        seniorLocationRepository.save(
                SeniorLocation.of(memberId, latitude, longitude, orderAtMs, request.tsMs(), receivedAtMs));
        appendTrailQuietly(memberId, latitude, longitude, orderAtMs);
        broadcastQuietly(memberId, response);
        publishLocationUpdatedQuietly(memberId, latitude, longitude);
    }

    private void appendTrailQuietly(Long memberId, Double latitude, Double longitude, Long orderAtMs) {
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + memberId;
        try {
            Long listSize = redisTemplate.opsForList()
                    .leftPush(trailKey, LocationPoint.of(latitude, longitude, orderAtMs));
            redisTemplate.opsForList().trim(trailKey, 0, TRAIL_MAX_POINTS - 1);
            redisTemplate.expire(trailKey, TRAIL_TTL_SECONDS, TimeUnit.SECONDS);
            log.info("이동 경로 저장: memberId={}, pointCount={}", memberId, listSize);
        } catch (RuntimeException e) {
            log.warn("이동 경로 저장 실패: memberId={}, cause={}", memberId, e.getClass().getSimpleName());
        }
    }

    private void broadcastQuietly(Long memberId, LocationUpdateResponse response) {
        String destination = String.format("/topic/location/senior/%d", memberId);
        try {
            messagingTemplate.convertAndSend(destination, response);
            log.info("시니어 방으로 위치 브로드캐스트 완료 - memberId: {}, destination: {}", memberId, destination);
        } catch (RuntimeException e) {
            log.warn("위치 브로드캐스트 실패: memberId={}, cause={}", memberId, e.getClass().getSimpleName());
        }
    }

    private void publishLocationUpdatedQuietly(Long memberId, Double latitude, Double longitude) {
        try {
            eventPublisher.publishEvent(new SeniorLocationUpdatedEvent(memberId, latitude, longitude));
        } catch (RuntimeException e) {
            log.warn("위치 갱신 이벤트 발행 실패: memberId={}, cause={}", memberId, e.getClass().getSimpleName());
        }
    }

    /**
     * 보호자가 추적 가능한 시니어 목록 조회
     */
    public List<TrackedSeniorResponse> getTrackedSeniors(Long guardianId) {
        FamilyMembership myMembership = familyMembershipRepository.findByGuardianId(guardianId)
                .orElse(null);

        if (myMembership == null) {
            return List.of();
        }

        List<SeniorProfile> seniors = seniorProfileRepository
                .findAllByFamilyIdWithMember(myMembership.getFamily().getId());

        List<Long> seniorMemberIds = seniors.stream()
                .map(s -> s.getMember().getId())
                .toList();

        Map<Long, SeniorLocation> locationMap = new java.util.HashMap<>();
        seniorLocationRepository.findAllById(seniorMemberIds)
                .forEach(l -> locationMap.put(l.getSeniorId(), l));

        recordAccessQuietly(guardianId, seniorMemberIds, LocationAccessPath.REST_FAMILY);

        long nowMs = System.currentTimeMillis();
        return seniors.stream()
                .map(senior -> {
                    Long memberId = senior.getMember().getId();
                    SeniorLocation location = locationMap.get(memberId);

                    if (location != null) {
                        return TrackedSeniorResponse.of(senior, location.getLatitude(), location.getLongitude(),
                                freshnessOf(location, nowMs));
                    }

                    StayInfo stayInfo = getStayInfo(memberId);
                    if (stayInfo != null) {
                        return TrackedSeniorResponse.of(senior, stayInfo.latitude(), stayInfo.longitude(),
                                LocationFreshness.unknown(LocationSource.STAY));
                    }

                    return TrackedSeniorResponse.withoutLocation(senior);
                })
                .toList();
    }

    public Boolean getOutingStatus(Long memberId) {
        StayInfo stayInfo = getStayInfo(memberId);
        if (stayInfo == null) {
            return null;
        }
        return !LocationType.HOME.name().equals(stayInfo.locationType());
    }

    /**
     * 특정 시니어의 마지막 위치 조회 (REST API용)
     * @param memberId 시니어의 Member ID
     * @param guardianId 보호자의 Member ID
     */
    public LocationUpdateResponse getLastLocation(Long memberId, Long guardianId) {

        // 시니어 프로필 조회 (memberId로)
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));

        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(guardianId, seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }

        recordAccessQuietly(guardianId, memberId, LocationAccessPath.REST_LAST);

        Member seniorMember = seniorProfile.getMember();

        // 체류 시작 시각과 위치 종류는 체류 상태(MySQL)에서 읽는다. 없으면 비운다(지금 시각으로 채우지 않는다).
        LocalDateTime stayStartTime = null;
        String locationType = null;
        StayInfo stayInfo = getStayInfo(memberId);
        if (stayInfo != null) {
            stayStartTime = stayInfo.startTime();
            locationType = stayInfo.locationType();
        }

        // Redis 최신 위치를 먼저 쓰고, 없으면 체류 지점 좌표를 쓴다. 체류 좌표는 마지막으로 잰 시각을 모른다.
        Optional<SeniorLocation> locationOpt = seniorLocationRepository.findBySeniorId(memberId);

        double latitude;
        double longitude;
        LocationFreshness freshness;

        if (locationOpt.isPresent()) {
            latitude = locationOpt.get().getLatitude();
            longitude = locationOpt.get().getLongitude();
            freshness = freshnessOf(locationOpt.get(), System.currentTimeMillis());
        } else if (stayInfo != null) {
            latitude = stayInfo.latitude();
            longitude = stayInfo.longitude();
            freshness = LocationFreshness.unknown(LocationSource.STAY);
        } else {
            throw new BusinessException(ErrorCode.NOT_FOUND, "최근 위치 정보가 없습니다.");
        }

        String locationName = getStayLocationName(stayInfo);

        return LocationUpdateResponse.of(
                memberId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                latitude,
                longitude,
                stayStartTime,
                locationType,
                locationName,
                freshness
        );
    }

    private static LocationFreshness freshnessOf(SeniorLocation location, long nowMs) {
        return LocationFreshness.of(location.displayMeasuredAtMs(), location.getReceivedAtMs(),
                LocationSource.LATEST, nowMs);
    }

    /** 조회 API용 체류 정보. 권위 있는 저장소인 MySQL 체류 상태에서 읽는다(LLD-0141). */
    private StayInfo getStayInfo(Long memberId) {
        return stayStateService.find(memberId).map(RealtimeLocationService::toStayInfo).orElse(null);
    }

    private static StayInfo toStayInfo(SeniorStayState state) {
        return new StayInfo(state.getLatitude(), state.getLongitude(), state.getStayStartedAt(),
                state.getLocationType(), state.getLocationName());
    }

    /**
     * 특정 시니어의 15분 이동 경로 조회
     * @param memberId 시니어의 Member ID
     * @param guardianId 보호자의 Member ID
     */
    public LocationTrailResponse getLocationTrail(Long memberId, Long guardianId) {

        // 시니어 프로필 조회 (memberId로)
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));

        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(guardianId, seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }

        recordAccessQuietly(guardianId, memberId, LocationAccessPath.REST_TRAIL);

        Member seniorMember = seniorProfile.getMember();

        // Redis에서 이동 경로 조회 (memberId 기준)
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + memberId;
        List<Object> rawTrail = redisTemplate.opsForList().range(trailKey, 0, TRAIL_MAX_POINTS - 1);

        // LocationPoint로 변환 (LinkedHashMap으로 역직렬화된 경우 처리). 최근 15분 안에 잰 점만 남긴다(LLD-0141).
        long cutoffMs = System.currentTimeMillis() - TRAIL_WINDOW_MS;
        List<LocationPoint> trail = new ArrayList<>();
        if (rawTrail != null) {
            for (Object obj : rawTrail) {
                LocationPoint point = toLocationPoint(obj);
                if (point != null && isWithinTrailWindow(point, cutoffMs)) {
                    trail.add(point);
                }
            }
        }

        java.util.Collections.reverse(trail);

        log.info("이동 경로 조회 완료 - memberId: {}, 포인트 개수: {}", memberId, trail.size());

        return LocationTrailResponse.of(
                memberId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                trail
        );
    }

    private LocationPoint toLocationPoint(Object obj) {
        if (obj instanceof LocationPoint point) {
            return point;
        }
        if (obj instanceof LinkedHashMap<?, ?> map) {
            // GenericJackson2JsonRedisSerializer로 역직렬화 시 LinkedHashMap으로 변환되는 경우
            Double lat = getDoubleOrNull(map.get("latitude"));
            Double lng = getDoubleOrNull(map.get("longitude"));
            if (lat == null || lng == null) {
                return null;
            }
            return new LocationPoint(lat, lng, parseTimestamp(map.get("timestamp")),
                    getLongOrNull(map.get("measuredAtMs")));
        }
        return null;
    }

    /**
     * 측정 시각(순서 시각) 기준으로 최근 15분 안의 점인지 본다. 측정 시각이 없는 예전 점은 저장 시각으로 본다.
     * 위치마다 키 TTL을 15분으로 다시 걸어, 계속 움직이는 동안에는 오래된 점이 리스트에 남으므로 읽을 때 거른다.
     */
    private static boolean isWithinTrailWindow(LocationPoint point, long cutoffMs) {
        if (point.measuredAtMs() != null) {
            return point.measuredAtMs() >= cutoffMs;
        }
        if (point.timestamp() == null) {
            return false;
        }
        return point.timestamp().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() >= cutoffMs;
    }

    private static Long getLongOrNull(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /**
     * 체류 정보 계산과 체류, 안전구역 상태 저장(LLD-0141). 위치 처리 DB 트랜잭션 안에서 돈다.
     * - 체류 지점에서 30m 이내면 기존 체류를 유지하고 상태 행을 쓰지도 잠그지도 않는다.
     * - 30m 넘게 움직였으면 행을 잠그고 최신 값으로 다시 판정한 뒤 새 체류로 바꾼다.
     * - 안전구역(HOME, OTHER) 75m 반경 내면 해당 타입, 아니면 null.
     * - 안전구역 이탈, 복귀 알림은 상태 행의 30분 묶음으로 정하고, 알림 Outbox는 같은 트랜잭션에 들어간다.
     *
     * <p>상태와 Outbox가 함께 커밋되거나 함께 롤백되므로 예전 Redis 표식의 롤백 보정이 필요 없다. 같은 회원의 처리는
     * JVM 순서 잠금이 한 번에 하나씩 보내고, DB 행 잠금은 상태를 바꾸는 순간의 마지막 방어선이다.
     */
    private StayInfo calculateStayInfo(Double newLat, Double newLng, Member member) {
        Long memberId = member.getId();
        SeniorStayState current = stayStateService.find(memberId).orElse(null);
        if (isSameStay(current, newLat, newLng)) {
            log.debug("같은 위치 유지 - memberId: {}", memberId);
            return toStayInfo(current);
        }

        SeniorStayState locked = stayStateService.lockForChange(memberId).orElse(null);
        if (isSameStay(locked, newLat, newLng)) {
            return toStayInfo(locked);
        }

        ParentLocation matchedZone = determineMatchingSafeZone(member, newLat, newLng);
        String locationType = getLocationTypeName(matchedZone);
        String locationName = getLocationName(matchedZone);
        LocalDateTime now = LocalDateTime.now();

        String previousLocationType = null;
        if (locked == null) {
            locked = stayStateService.create(
                    SeniorStayState.start(memberId, newLat, newLng, now, locationType, locationName));
        } else {
            previousLocationType = locked.getLocationType();
            locked.moveTo(newLat, newLng, now, locationType, locationName);
        }
        safeZoneAlertService.handleSafeZoneTransition(locked, previousLocationType, locationType, now);
        log.debug("새로운 위치로 이동 - memberId: {}, 위치 타입: {}", memberId, locationType);
        return toStayInfo(locked);
    }

    private static boolean isSameStay(SeniorStayState state, Double newLat, Double newLng) {
        return state != null && GeoUtils.isWithinRadius(
                state.getLatitude(), state.getLongitude(), newLat, newLng, STAY_RADIUS_METERS);
    }

    /**
     * 현재 위치가 어떤 안전구역 내에 있는지 판단
     * - 등록된 모든 안전구역(HOME, OTHER)과 75m 반경(지름 150m) 내 비교
     * - HOME 안전구역 내 → HOME
     * - OTHER 안전구역 내 → OTHER
     * - 어떤 안전구역에도 없으면 → null
     */
    private ParentLocation determineMatchingSafeZone(Member member, Double lat, Double lng) {
        List<ParentLocation> safeZones = parentLocationRepository.findAllByMember(member);

        for (ParentLocation safeZone : safeZones) {
            boolean isWithinSafeZone = GeoUtils.isWithinRadius(
                    lat, lng, safeZone.getLatitude(), safeZone.getLongitude(), SAFE_ZONE_RADIUS_METERS);
            if (isWithinSafeZone) {
                return safeZone;
            }
        }

        return null;
    }

    /**
     * Redis에서 역직렬화된 timestamp 파싱
     */
    private LocalDateTime parseTimestamp(Object timestampObj) {
        if (timestampObj == null) {
            return LocalDateTime.now();
        }
        if (timestampObj instanceof String str) {
            return LocalDateTime.parse(str);
        }
        if (timestampObj instanceof LinkedHashMap<?, ?> map) {
            // LocalDateTime이 LinkedHashMap으로 역직렬화된 경우
            int year = getIntOrDefault(map.get("year"), 2024);
            int month = getIntOrDefault(map.get("monthValue"), 1);
            int day = getIntOrDefault(map.get("dayOfMonth"), 1);
            int hour = getIntOrDefault(map.get("hour"), 0);
            int minute = getIntOrDefault(map.get("minute"), 0);
            int second = getIntOrDefault(map.get("second"), 0);
            int nano = getIntOrDefault(map.get("nano"), 0);
            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        }
        return LocalDateTime.now();
    }

    private int getIntOrDefault(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private Double getDoubleOrNull(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    private String getStayLocationName(StayInfo stayInfo) {
        if (stayInfo == null) {
            return null;
        }
        return stayInfo.locationName();
    }

    private String getLocationTypeName(ParentLocation matchedZone) {
        if (matchedZone == null) {
            return null;
        }
        return matchedZone.getLocationType().name();
    }

    private String getLocationName(ParentLocation matchedZone) {
        if (matchedZone == null) {
            return null;
        }
        return matchedZone.getName();
    }

    /**
     * 위치 열람 사실을 남긴다(LLD-0056 5.1). 기록·통보가 깨져도 위치 조회는 성공해야 하므로
     * 여기서 모든 예외를 삼킨다. 로그에는 좌표를 남기지 않는다.
     */
    private void recordAccessQuietly(Long guardianId, Long seniorMemberId, LocationAccessPath path) {
        try {
            locationAccessLogService.record(guardianId, seniorMemberId, path);
        } catch (Exception e) {
            log.warn("위치 열람 기록 실패: path={}, cause={}", path, e.getClass().getSimpleName());
        }
    }

    private void recordAccessQuietly(Long guardianId, List<Long> seniorMemberIds, LocationAccessPath path) {
        for (Long seniorMemberId : seniorMemberIds) {
            recordAccessQuietly(guardianId, seniorMemberId, path);
        }
    }

    /**
     * 4분마다 실행 — SeniorLocation(5분 TTL)의 만료 시간을 늘려 시니어가 정지 중이어도 마지막 위치가 사라지지 않게 한다.
     * 마지막 위치를 남겨 두는 것이지 새 위치로 만드는 것이 아니다. 신선도는 응답의 측정 시각으로 판단한다(LLD-0141).
     * 활성 회원이 아니면 갱신하지 않고 지운다. → LLD-0059
     *
     * <p>위치 본문은 다시 저장하지 않고 만료 시간만 늘린다(LLD-0140). 읽어 둔 위치를 다시 저장하면, 읽은 뒤 위치 수신이
     * 저장한 새 위치를 옛 위치로 덮는다. 키가 이미 없으면(탈퇴 삭제, 만료) 만료 연장은 아무것도 하지 않는다.
     *
     * <p>체류 상태는 MySQL에 있어 만료가 없으므로 활성이 아닌 회원의 행을 여기서 함께 지운다. 활성 회원 조회는 짧은 읽기 트랜잭션에서 끝내 커넥션을 쥔 채 삭제
     * 트랜잭션이 두 번째 커넥션을 기다리지 않게 한다.
     */
    @Scheduled(fixedRate = 240_000)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshLocationTtl() {
        // 최신 위치가 만료된 비활성 회원의 체류 상태 행도 지운다(Redis 키처럼 스스로 만료되지 않는다).
        stayStateService.findInactiveMemberIds().forEach(this::deleteLocation);
        List<SeniorLocation> locations = new ArrayList<>();
        seniorLocationRepository.findAll().forEach(location -> {
            if (location != null) {
                locations.add(location);
            }
        });
        if (locations.isEmpty()) {
            return;
        }
        List<Long> memberIds = locations.stream().map(SeniorLocation::getSeniorId).toList();
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        Set<Long> activeMemberIds = readOnly.execute(status -> memberRepository.findActiveIdsIn(memberIds));
        for (SeniorLocation location : locations) {
            if (!activeMemberIds.contains(location.getSeniorId())) {
                deleteLocation(location.getSeniorId());
                continue;
            }
            redisTemplate.expire(SENIOR_LOCATION_KEY_PREFIX + location.getSeniorId(),
                    SeniorLocation.TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 탈퇴하거나 활성이 아닌 회원의 위치를 지운다: Redis 현재 위치, 이동 경로, 예전 체류 키와 MySQL 체류 상태. → LLD-0059, LLD-0141
     * 체류 상태 삭제는 자기 트랜잭션에서 끝난다(탈퇴 커밋 뒤 리스너에서도 부른다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteLocation(Long memberId) {
        try {
            stayStateService.delete(memberId);
        } finally {
            // DB 삭제가 실패해도 Redis 위치는 지우고, Redis 삭제가 실패해도 DB 행은 이미 지웠다.
            seniorLocationRepository.deleteById(memberId);
            redisTemplate.delete(List.of(LOCATION_TRAIL_KEY_PREFIX + memberId, LEGACY_STAY_KEY_PREFIX + memberId));
        }
    }
}
