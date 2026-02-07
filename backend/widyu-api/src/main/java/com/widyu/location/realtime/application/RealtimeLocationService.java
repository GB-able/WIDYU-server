package com.widyu.location.realtime.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.GeoUtils;
import com.widyu.location.SeniorLocation;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.ParentLocation;
import com.widyu.fcm.event.safezone.dto.SafeZoneExitEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RealtimeLocationService {

    private final SeniorLocationRepository seniorLocationRepository;
    private final FamilyConnectionRepository familyConnectionRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final ParentLocationRepository parentLocationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    private static final String LOCATION_TRAIL_KEY_PREFIX = "location:trail:";
    private static final String LOCATION_STAY_KEY_PREFIX = "location:stay:";
    private static final String SAFE_ZONE_ALERT_KEY_PREFIX = "safezone:alert:";
    private static final long TRAIL_TTL_SECONDS = 900; // 15분
    private static final long STAY_TTL_SECONDS = 86400; // 24시간
    private static final long SAFE_ZONE_ALERT_TTL_SECONDS = 1800; // 30분 (중복 알림 방지)
    private static final double STAY_RADIUS_METERS = 30.0; // 30m 이내면 같은 위치
    private static final double SAFE_ZONE_RADIUS_METERS = 75.0; // 안전구역 반경 75m (지름 150m)

    @Transactional
    public LocationUpdateResponse updateAndBroadcast(LocationUpdateRequest request,
                                                      Long authenticatedMemberId) {

        Long memberId = request.memberId();

        // 1. 권한 검증: 시니어 본인인지 확인
        if (!memberId.equals(authenticatedMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 위치만 업데이트할 수 있습니다.");
        }

        // 2. 시니어 프로필 조회 (memberId로)
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));

        Member seniorMember = seniorProfile.getMember();

        // 3. Redis에 최신 위치 저장 (memberId 기준)
        SeniorLocation location = SeniorLocation.of(
                memberId,
                request.latitude(),
                request.longitude()
        );
        seniorLocationRepository.save(location);

        // 4. Redis List에 이동 경로 저장 (15분 TTL, memberId 기준)
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + memberId;
        LocationPoint point = LocationPoint.of(request.latitude(), request.longitude());

        redisTemplate.opsForList().leftPush(trailKey, point);
        redisTemplate.expire(trailKey, TRAIL_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("Redis에 위치 및 이동 경로 저장 완료 - memberId: {}", memberId);

        // 5. 체류 시간 및 위치 타입 계산 (memberId 기준)
        String stayKey = LOCATION_STAY_KEY_PREFIX + memberId;
        StayInfo stayInfo = calculateStayInfo(
                stayKey, request.latitude(), request.longitude(), seniorMember);

        // 6. Response 객체 생성
        LocationUpdateResponse response = LocationUpdateResponse.of(
                memberId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                request.latitude(),
                request.longitude(),
                stayInfo.startTime(),
                stayInfo.locationType()
        );

        // 7. 시니어별 방으로 브로드캐스트 (memberId 기준)
        String destination = String.format("/topic/location/senior/%d", memberId);
        messagingTemplate.convertAndSend(destination, response);

        log.info("시니어 방으로 위치 브로드캐스트 완료 - memberId: {}, destination: {}",
                 memberId, destination);

        return response;
    }

    /**
     * 보호자가 추적 가능한 시니어 목록 조회
     */
    public List<TrackedSeniorResponse> getTrackedSeniors(Long guardianId) {

        // 가족 연결 정보 조회 (Senior와 Member join fetch)
        List<FamilyConnection> connections = familyConnectionRepository
                .findAllByGuardianIdWithSeniorAndMember(guardianId);

        // DTO로 변환
        List<TrackedSeniorResponse> seniors = connections.stream()
                .map(TrackedSeniorResponse::from)
                .toList();

        return seniors;
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

        Long seniorId = seniorProfile.getId();

        // 권한 검증: 가족 연결 확인 (SeniorProfile.id로)
        if (!familyConnectionRepository.existsBySeniorIdAndGuardianId(seniorId, guardianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }

        // Redis에서 조회 (memberId 기준)
        SeniorLocation location = seniorLocationRepository.findBySeniorId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                                          "최근 위치 정보가 없습니다."));

        Member seniorMember = seniorProfile.getMember();

        // 체류 시간 및 위치 타입 정보 조회 (memberId 기준)
        String stayKey = LOCATION_STAY_KEY_PREFIX + memberId;
        Object stayObj = redisTemplate.opsForValue().get(stayKey);
        LocalDateTime stayStartTime = LocalDateTime.now();
        String locationType = null;

        if (stayObj instanceof StayInfo stayInfo) {
            stayStartTime = stayInfo.startTime();
            locationType = stayInfo.locationType();
        }

        return LocationUpdateResponse.of(
                memberId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                location.getLatitude(),
                location.getLongitude(),
                stayStartTime,
                locationType
        );
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

        Long seniorId = seniorProfile.getId();

        // 권한 검증: 가족 연결 확인 (SeniorProfile.id로)
        if (!familyConnectionRepository.existsBySeniorIdAndGuardianId(seniorId, guardianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }

        Member seniorMember = seniorProfile.getMember();

        // Redis에서 이동 경로 조회 (memberId 기준)
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + memberId;
        List<Object> rawTrail = redisTemplate.opsForList().range(trailKey, 0, -1);

        // LocationPoint로 변환
        List<LocationPoint> trail = new ArrayList<>();
        if (rawTrail != null) {
            for (Object obj : rawTrail) {
                if (obj instanceof LocationPoint) {
                    trail.add((LocationPoint) obj);
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
        Object stayObj = redisTemplate.opsForValue().get(stayKey);
        String previousLocationType = null;

        if (stayObj instanceof StayInfo previousStay) {
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

        // 새로운 위치로 이동 → 위치 타입 계산
        String locationType = determineLocationType(member, newLat, newLng);

        // 안전구역 이탈 감지 및 알림 전송
        checkAndSendSafeZoneExitAlert(member, previousLocationType, locationType);

        StayInfo newStay = StayInfo.of(newLat, newLng, locationType);
        redisTemplate.opsForValue().set(stayKey, newStay, STAY_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("새로운 위치로 이동 - stayKey: {}, 체류 시작: {}, 위치 타입: {}",
                stayKey, newStay.startTime(), locationType);

        return newStay;
    }

    /**
     * 현재 위치가 어떤 안전구역 내에 있는지 판단
     * - 등록된 모든 안전구역(HOME, OTHER)과 75m 반경(지름 150m) 내 비교
     * - HOME 안전구역 내 → HOME
     * - OTHER 안전구역 내 → OTHER
     * - 어떤 안전구역에도 없으면 → null
     */
    private String determineLocationType(Member member, Double lat, Double lng) {
        List<ParentLocation> safeZones = parentLocationRepository.findAllByMember(member);

        for (ParentLocation safeZone : safeZones) {
            double zoneLat = Double.parseDouble(safeZone.getLatitude());
            double zoneLng = Double.parseDouble(safeZone.getLongitude());

            boolean isWithinSafeZone = GeoUtils.isWithinRadius(lat, lng, zoneLat, zoneLng, SAFE_ZONE_RADIUS_METERS);

            if (isWithinSafeZone) {
                return safeZone.getLocationType().name();
            }
        }

        return null;
    }

    /**
     * 안전구역 이탈 감지 및 보호자 알림 전송
     * - 이전 위치가 안전구역 내(HOME 또는 OTHER)였고, 현재 위치가 안전구역 밖(null)이면 알림
     * - 중복 알림 방지: 30분 내 동일 알림 전송하지 않음
     * - 안전구역에 다시 들어오면 알림 플래그 삭제
     */
    private void checkAndSendSafeZoneExitAlert(Member member, String previousLocationType, String currentLocationType) {
        String alertKey = SAFE_ZONE_ALERT_KEY_PREFIX + member.getId();

        // 안전구역에 다시 들어온 경우 → 알림 플래그 삭제
        if (currentLocationType != null) {
            redisTemplate.delete(alertKey);
            return;
        }

        // 이전에 안전구역 내에 있었고, 현재 안전구역 밖으로 나간 경우
        if (previousLocationType != null && currentLocationType == null) {
            // 중복 알림 방지: 이미 알림을 보낸 경우 스킵
            Boolean alreadySent = redisTemplate.hasKey(alertKey);
            if (Boolean.TRUE.equals(alreadySent)) {
                log.debug("안전구역 이탈 알림 스킵 (중복 방지) - memberId: {}", member.getId());
                return;
            }

            // 알림 플래그 설정 (30분 TTL)
            redisTemplate.opsForValue().set(alertKey, true, SAFE_ZONE_ALERT_TTL_SECONDS, TimeUnit.SECONDS);

            // 안전구역 이탈 이벤트 발행 → FCM 리스너에서 알림 전송
            eventPublisher.publishEvent(new SafeZoneExitEvent(member.getId()));
            log.info("안전구역 이탈 이벤트 발행 - memberId: {}", member.getId());
        }
    }
}
