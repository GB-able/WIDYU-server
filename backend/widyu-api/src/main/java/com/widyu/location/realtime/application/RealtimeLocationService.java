package com.widyu.location.realtime.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.global.config.RedisConfig;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.GeoUtils;
import com.widyu.location.SeniorLocation;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.raw.application.LocationFixService;
import com.widyu.location.realtime.dto.LocationAckResponse;
import com.widyu.location.realtime.dto.LocationAckResult;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    // 미지정 필드를 무시하고 페이로드를 읽는 전용 매퍼. 주입받지 않는 이유는 Redis·응답 직렬화와
    // 설정을 공유하면 안 되기 때문이다(초기화 필드라 생성자 주입 대상에서 빠진다).
    private final ObjectMapper payloadMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    // Redis 값은 타입 정보 없이 저장돼 LinkedHashMap으로 읽힌다. 저장할 때와 같은 설정으로 되돌린다(LLD-0097).
    private final ObjectMapper redisValueMapper = RedisConfig.redisObjectMapper();

    private static final String LOCATION_TRAIL_KEY_PREFIX = "location:trail:";
    // @RedisHash 저장소가 쓰는 키 모양(keyspace:id)과 같아야 만료 연장이 그 위치에 걸린다.
    private static final String SENIOR_LOCATION_KEY_PREFIX = SeniorLocation.KEYSPACE + ":";
    private static final String LOCATION_STAY_KEY_PREFIX = "location:stay:";
    private static final long TRAIL_TTL_SECONDS = 900; // 15분
    // 이동 경로 점 상한. 초당 1건으로 보내도 15분을 담는다. 위치마다 TTL을 다시 걸어 키가 만료되지 않으므로
    // 자르지 않으면 리스트가 끝없이 커진다(LLD-0098).
    private static final long TRAIL_MAX_POINTS = 900;
    private static final long STAY_TTL_SECONDS = 86400; // 24시간
    private static final double STAY_RADIUS_METERS = 30.0; // 30m 이내면 같은 위치
    private static final double SAFE_ZONE_RADIUS_METERS = 75.0; // 안전구역 반경 75m (지름 150m)
    // 응급 이동 경로는 최근 15분만 보여 준다. 그보다 오래 전에 잰 위치는 실시간 경로에 반영하지 않는다(LLD-0114).
    private static final long REALTIME_MAX_AGE_MS = Duration.ofMinutes(15).toMillis();

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
            return LocationAckResponse.legacy(applyRealtime(request, authenticatedMemberId));
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
            response = applyRealtime(request, authenticatedMemberId);
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
    private LocationUpdateResponse applyRealtime(LocationUpdateRequest request, Long authenticatedMemberId) {
        verifySelf(request.memberId(), authenticatedMemberId);
        if (isTooOld(request)) {
            return skipped(request);
        }
        return sequencer.run(request.memberId(), request.tsMs(), () -> {
            // 차례를 기다리는 동안 15분을 넘길 수 있어 잠금 안에서 다시 본다.
            if (isTooOld(request) || !isNewerThanLatest(request)) {
                return skipped(request);
            }
            return updateAndBroadcast(request, authenticatedMemberId);
        }, () -> superseded(request));
    }

    /**
     * 같은 회원의 더 늦게 잰 위치가 이미 처리 중이거나 기다리고 있어 이 위치의 실시간 처리를 생략한다(LLD-0140).
     * 반영해도 곧 더 최근 위치로 바뀌므로 할 일이 없다. 원본 저장 결과로 ACK한다. 더 최근 위치가 실패하면 그 위치는
     * ACK를 받지 못해 다시 오므로 최신 위치는 그 재전송에서 맞춰진다.
     */
    private LocationUpdateResponse superseded(LocationUpdateRequest request) {
        log.info("위치 실시간 반영 생략(더 최근 위치 처리 중): memberId={}, seq={}", request.memberId(), request.seq());
        return LocationUpdateResponse.received(
                request.memberId(), request.resolvedLatitude(), request.resolvedLongitude());
    }

    private LocationUpdateResponse skipped(LocationUpdateRequest request) {
        log.info("위치 실시간 반영 생략: memberId={}, seq={}", request.memberId(), request.seq());
        return LocationUpdateResponse.received(
                request.memberId(), request.resolvedLatitude(), request.resolvedLongitude());
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

    private boolean isNewerThanLatest(LocationUpdateRequest request) {
        Long measuredAtMs = request.tsMs();
        if (measuredAtMs == null) {
            return true;
        }
        Long latestMeasuredAtMs = seniorLocationRepository.findById(request.memberId())
                .map(SeniorLocation::getMeasuredAtMs)
                .orElse(null);
        if (latestMeasuredAtMs == null) {
            return true;
        }
        return measuredAtMs > latestMeasuredAtMs;
    }

    /**
     * 최신 위치의 비교 기준에는 미래 시각을 남기지 않는다. 기기 시계가 앞서 미래로 잰 위치가 기준이 되면
     * 그 뒤 정상 시각의 위치가 모두 "최신보다 먼저 잰 위치"로 빠지고, TTL 갱신이 그 값을 계속 살려 둔다.
     */
    private static Long notAfterNow(Long measuredAtMs) {
        if (measuredAtMs == null) {
            return null;
        }
        return Math.min(measuredAtMs, System.currentTimeMillis());
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
        LocationUpdateResponse response = new TransactionTemplate(transactionManager)
                .execute(status -> decideInTransaction(request, authenticatedMemberId));
        publishAfterTransaction(request, response);
        return response;
    }

    /** DB 트랜잭션 안에서 할 일: 프로필 조회, 체류·안전구역 판정(이탈 시 알림 예약), 응답 생성. */
    private LocationUpdateResponse decideInTransaction(LocationUpdateRequest request, Long authenticatedMemberId) {
        Long memberId = request.memberId();
        verifySelf(memberId, authenticatedMemberId);

        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));
        Member seniorMember = seniorProfile.getMember();

        String stayKey = LOCATION_STAY_KEY_PREFIX + memberId;
        StayInfo stayInfo = calculateStayInfo(
                stayKey, request.resolvedLatitude(), request.resolvedLongitude(), seniorMember);

        return LocationUpdateResponse.of(
                memberId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                request.resolvedLatitude(),
                request.resolvedLongitude(),
                stayInfo.startTime(),
                stayInfo.locationType(),
                stayInfo.locationName()
        );
    }

    /**
     * 트랜잭션이 끝난 뒤 할 일. 최신 위치 저장이 실패하면 오류로 알린다. 저장되지 않았으니 같은 측정 시각의 재전송이
     * 실시간 후보로 다시 평가돼 복구된다. 그 뒤 단계는 최신 위치가 이미 저장돼 같은 시각의 재전송이 걸러지므로,
     * 오류를 돌려줘도 복구되지 않는다. 그래서 실패를 남기고 나머지를 계속한다.
     */
    private void publishAfterTransaction(LocationUpdateRequest request, LocationUpdateResponse response) {
        Long memberId = request.memberId();
        Double latitude = request.resolvedLatitude();
        Double longitude = request.resolvedLongitude();
        seniorLocationRepository.save(SeniorLocation.of(memberId, latitude, longitude, notAfterNow(request.tsMs())));
        appendTrailQuietly(memberId, latitude, longitude);
        broadcastQuietly(memberId, response);
        publishLocationUpdatedQuietly(memberId, latitude, longitude);
    }

    private void appendTrailQuietly(Long memberId, Double latitude, Double longitude) {
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + memberId;
        try {
            Long listSize = redisTemplate.opsForList().leftPush(trailKey, LocationPoint.of(latitude, longitude));
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

        return seniors.stream()
                .map(senior -> {
                    Long memberId = senior.getMember().getId();
                    SeniorLocation location = locationMap.get(memberId);

                    if (location != null) {
                        return TrackedSeniorResponse.of(senior, location.getLatitude(), location.getLongitude());
                    }

                    StayInfo stayInfo = getStayInfo(memberId);
                    if (stayInfo != null) {
                        return TrackedSeniorResponse.of(senior, stayInfo.latitude(), stayInfo.longitude());
                    }

                    return TrackedSeniorResponse.of(senior, null, null);
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

        // 체류 시간 및 위치 타입 정보 조회 (memberId 기준)
        LocalDateTime stayStartTime = LocalDateTime.now();
        String locationType = null;
        StayInfo stayInfo = null;

        stayInfo = getStayInfo(memberId);
        if (stayInfo != null) {
            stayStartTime = stayInfo.startTime();
            locationType = stayInfo.locationType();
        }

        // SeniorLocation(5분 TTL) 우선 조회, 만료 시 StayInfo(24시간 TTL)로 fallback
        Optional<SeniorLocation> locationOpt = seniorLocationRepository.findBySeniorId(memberId);

        double latitude;
        double longitude;

        if (locationOpt.isPresent()) {
            latitude = locationOpt.get().getLatitude();
            longitude = locationOpt.get().getLongitude();
        } else if (stayInfo != null) {
            latitude = stayInfo.latitude();
            longitude = stayInfo.longitude();
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
                locationName
        );
    }

    /**
     * 조회 API용 체류 정보. Redis 연결·시간 초과는 체류 없음으로 넘긴다(화면이 비는 편이 오류보다 낫다).
     * 안전구역 판정에는 쓰지 않는다. 판정에서 오류를 없음으로 바꾸면 이전 "안" 상태를 잃고 알림 없이 "밖"을 저장한다.
     */
    private StayInfo getStayInfo(Long memberId) {
        try {
            return readStay(memberId);
        } catch (DataAccessException e) {
            log.warn("체류 정보 조회 실패 - memberId: {}, cause: {}", memberId, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 체류 정보를 읽는 유일한 경로다(LLD-0097). 값이 깨졌을 때만 지운다. 연결·시간 초과에 지우면 정상 체류 정보를
     * 잃어 다음 이탈을 놓친다. 연결·시간 초과는 호출한 쪽에 그대로 던진다(LLD-0121). Jackson 예외 메시지에는 원문
     * 좌표가 섞일 수 있어 예외 이름만 남긴다(LLD-0029).
     */
    private StayInfo readStay(Long memberId) {
        String stayKey = LOCATION_STAY_KEY_PREFIX + memberId;
        try {
            Object raw = redisTemplate.opsForValue().get(stayKey);
            if (raw == null) {
                return null;
            }
            StayInfo stay = redisValueMapper.convertValue(raw, StayInfo.class);
            // 좌표·시작 시각이 빠진 값은 이후 거리 계산·체류 시간에서 null로 터지므로 깨진 값과 같이 처리한다.
            if (stay.latitude() == null || stay.longitude() == null || stay.startTime() == null) {
                throw new IllegalArgumentException("체류 정보 필수 값 누락");
            }
            return stay;
        } catch (SerializationException | IllegalArgumentException e) {
            log.warn("체류 정보를 읽을 수 없어 삭제 - stayKey: {}, cause: {}", stayKey, e.getClass().getSimpleName());
            redisTemplate.delete(stayKey);
        }
        return null;
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

        // LocationPoint로 변환 (LinkedHashMap으로 역직렬화된 경우 처리)
        List<LocationPoint> trail = new ArrayList<>();
        if (rawTrail != null) {
            for (Object obj : rawTrail) {
                if (obj instanceof LocationPoint point) {
                    trail.add(point);
                } else if (obj instanceof LinkedHashMap<?, ?> map) {
                    // GenericJackson2JsonRedisSerializer로 역직렬화 시 LinkedHashMap으로 변환되는 경우
                    Double lat = getDoubleOrNull(map.get("latitude"));
                    Double lng = getDoubleOrNull(map.get("longitude"));
                    LocalDateTime timestamp = parseTimestamp(map.get("timestamp"));
                    if (lat != null && lng != null) {
                        trail.add(new LocationPoint(lat, lng, timestamp));
                    }
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

    /**
     * 체류 정보 계산 (체류 시작 시간 + 위치 타입)
     * - 이전 위치와 30m 이내면 기존 체류 정보 유지
     * - 30m 초과 이동 시 새로운 체류 정보 설정
     * - 안전구역(HOME, OTHER) 75m 반경 내면 해당 타입, 아니면 null
     * - 안전구역 이탈 시 보호자에게 알림 전송
     */
    private StayInfo calculateStayInfo(String stayKey, Double newLat, Double newLng, Member member) {
        // Redis 오류를 체류 없음으로 바꾸지 않는다. 판정을 멈추고 트랜잭션을 되돌려 다음 위치에서 다시 판정한다.
        StayInfo previousStay = readStay(member.getId());
        String previousLocationType = null;

        if (previousStay != null) {
            previousLocationType = previousStay.locationType();

            boolean isSameLocation = GeoUtils.isWithinRadius(
                    previousStay.latitude(), previousStay.longitude(),
                    newLat, newLng,
                    STAY_RADIUS_METERS
            );

            if (isSameLocation) {
                log.debug("같은 위치 유지 - stayKey: {}, 체류 시작: {}, 위치 타입: {}",
                        stayKey, previousStay.startTime(), previousStay.locationType());
                return previousStay;
            }
        }

        // 새로운 위치로 이동 → 안전구역 매칭
        ParentLocation matchedZone = determineMatchingSafeZone(member, newLat, newLng);
        String locationType = getLocationTypeName(matchedZone);
        String locationName = getLocationName(matchedZone);

        StayInfo newStay = StayInfo.of(newLat, newLng, locationType, locationName);
        // 이탈 리스너보다 먼저 등록해 커밋 직후 리스너가 새 체류 정보를 읽게 한다.
        saveStayAfterCommit(stayKey, newStay);
        // 안전구역 전이 감지: 이탈 사건은 커밋 뒤 열고, 재진입은 열린 사건의 상황을 종료한다.
        safeZoneAlertService.handleSafeZoneTransition(member.getId(), previousLocationType, locationType);
        log.debug("새로운 위치로 이동 - stayKey: {}, 체류 시작: {}, 위치 타입: {}",
                stayKey, newStay.startTime(), locationType);

        return newStay;
    }

    // 체류 정보는 안전구역 이탈 판정의 기준이다. 롤백된 갱신이 이를 바꾸면 같은 이탈을 다시 감지하지 못하므로 커밋 뒤에 저장한다 (LLD-0058)
    private void saveStayAfterCommit(String stayKey, StayInfo stay) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisTemplate.opsForValue().set(stayKey, stay, STAY_TTL_SECONDS, TimeUnit.SECONDS);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // afterCommit 예외는 이미 커밋된 요청을 실패로 돌려보낸다. 저장을 못 하면 다음 위치 갱신이 이전 체류 정보로 다시 판정한다
                try {
                    redisTemplate.opsForValue().set(stayKey, stay, STAY_TTL_SECONDS, TimeUnit.SECONDS);
                } catch (RuntimeException e) {
                    log.warn("커밋 후 체류 정보 저장 실패 - stayKey: {}", stayKey, e);
                }
            }
        });
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
     * 4분마다 실행 — SeniorLocation(5분 TTL)과 location:stay(24시간 TTL)의 만료 시간을 늘려
     * 시니어가 정지 중이어도 위치 데이터가 만료되지 않도록 유지한다.
     * 활성 회원이 아니면 갱신하지 않고 지운다. → LLD-0059
     *
     * <p>위치 본문은 다시 저장하지 않고 만료 시간만 늘린다(LLD-0140). 읽어 둔 위치를 다시 저장하면, 읽은 뒤 위치 수신이
     * 저장한 새 위치를 옛 위치로 덮는다. 이 작업은 회원별 순서 잠금을 거치지 않는다. 키가 이미 없으면(탈퇴 삭제, 만료)
     * 만료 연장은 아무것도 하지 않으므로 지운 위치를 되살리지도 않는다.
     */
    @Scheduled(fixedRate = 240_000)
    public void refreshLocationTtl() {
        List<SeniorLocation> locations = new ArrayList<>();
        seniorLocationRepository.findAll().forEach(location -> {
            if (location != null) {
                locations.add(location);
            }
        });
        if (locations.isEmpty()) {
            return;
        }
        Set<Long> activeMemberIds = memberRepository.findActiveIdsIn(
                locations.stream().map(SeniorLocation::getSeniorId).toList());
        for (SeniorLocation location : locations) {
            if (!activeMemberIds.contains(location.getSeniorId())) {
                deleteLocation(location.getSeniorId());
                continue;
            }
            redisTemplate.expire(SENIOR_LOCATION_KEY_PREFIX + location.getSeniorId(),
                    SeniorLocation.TTL_SECONDS, TimeUnit.SECONDS);
            String stayKey = LOCATION_STAY_KEY_PREFIX + location.getSeniorId();
            redisTemplate.expire(stayKey, STAY_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 탈퇴한 회원의 Redis 위치(현재 위치·이동 경로·체류)를 지운다. → LLD-0059
     */
    public void deleteLocation(Long memberId) {
        seniorLocationRepository.deleteById(memberId);
        redisTemplate.delete(List.of(LOCATION_TRAIL_KEY_PREFIX + memberId, LOCATION_STAY_KEY_PREFIX + memberId));
    }
}
