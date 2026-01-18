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
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCATION_TRAIL_KEY_PREFIX = "location:trail:";
    private static final String LOCATION_STAY_KEY_PREFIX = "location:stay:";
    private static final long TRAIL_TTL_SECONDS = 900; // 15분
    private static final long STAY_TTL_SECONDS = 86400; // 24시간
    private static final double STAY_RADIUS_METERS = 30.0; // 30m 이내면 같은 위치

    @Transactional
    public LocationUpdateResponse updateAndBroadcast(LocationUpdateRequest request,
                                                      Long authenticatedMemberId) {

        // 1. 시니어 프로필 조회
        SeniorProfile seniorProfile = seniorProfileRepository.findById(request.seniorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));

        // 2. 권한 검증: 시니어 본인인지 확인
        if (!seniorProfile.getMember().getId().equals(authenticatedMemberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 위치만 업데이트할 수 있습니다.");
        }

        // 3. Redis에 최신 위치 저장
        SeniorLocation location = SeniorLocation.of(
                request.seniorId(),
                request.latitude(),
                request.longitude()
        );
        seniorLocationRepository.save(location);

        // 4. Redis List에 이동 경로 저장 (15분 TTL)
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + request.seniorId();
        LocationPoint point = LocationPoint.of(request.latitude(), request.longitude());

        redisTemplate.opsForList().leftPush(trailKey, point);
        redisTemplate.expire(trailKey, TRAIL_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("Redis에 위치 및 이동 경로 저장 완료 - seniorId: {}", request.seniorId());

        // 5. 체류 시간 계산
        String stayKey = LOCATION_STAY_KEY_PREFIX + request.seniorId();
        LocalDateTime stayStartTime = calculateStayStartTime(
                stayKey, request.latitude(), request.longitude());

        // 6. Response 객체 생성
        Member seniorMember = seniorProfile.getMember();
        LocationUpdateResponse response = LocationUpdateResponse.of(
                request.seniorId(),
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                request.latitude(),
                request.longitude(),
                stayStartTime
        );

        // 7. 시니어별 방으로 브로드캐스트
        String destination = String.format("/topic/location/senior/%d", request.seniorId());
        messagingTemplate.convertAndSend(destination, response);

        log.info("시니어 방으로 위치 브로드캐스트 완료 - seniorId: {}, destination: {}",
                 request.seniorId(), destination);

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
     */
    public LocationUpdateResponse getLastLocation(Long seniorId, Long guardianId) {

        // 권한 검증: 가족 연결 확인
        if (!familyConnectionRepository.existsBySeniorIdAndGuardianId(seniorId, guardianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }

        // Redis에서 조회
        SeniorLocation location = seniorLocationRepository.findBySeniorId(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                                          "최근 위치 정보가 없습니다."));

        // 시니어 정보 조회
        SeniorProfile seniorProfile = seniorProfileRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST));

        Member seniorMember = seniorProfile.getMember();

        // 체류 시간 정보 조회
        String stayKey = LOCATION_STAY_KEY_PREFIX + seniorId;
        Object stayObj = redisTemplate.opsForValue().get(stayKey);
        LocalDateTime stayStartTime = LocalDateTime.now();

        if (stayObj instanceof StayInfo stayInfo) {
            stayStartTime = stayInfo.startTime();
        }

        return LocationUpdateResponse.of(
                seniorId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                location.getLatitude(),
                location.getLongitude(),
                stayStartTime
        );
    }

    /**
     * 특정 시니어의 15분 이동 경로 조회
     */
    public LocationTrailResponse getLocationTrail(Long seniorId, Long guardianId) {

        // 권한 검증: 가족 연결 확인
        if (!familyConnectionRepository.existsBySeniorIdAndGuardianId(seniorId, guardianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }

        // 시니어 정보 조회
        SeniorProfile seniorProfile = seniorProfileRepository.findById(seniorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));

        Member seniorMember = seniorProfile.getMember();

        // Redis에서 이동 경로 조회
        String trailKey = LOCATION_TRAIL_KEY_PREFIX + seniorId;
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

        log.info("이동 경로 조회 완료 - seniorId: {}, 포인트 개수: {}", seniorId, trail.size());

        return LocationTrailResponse.of(
                seniorId,
                seniorMember.getName(),
                seniorMember.getProfileImage(),
                trail
        );
    }

    /**
     * 체류 시작 시간 계산
     * - 이전 위치와 30m 이내면 기존 체류 시작 시간 유지
     * - 30m 초과 이동 시 새로운 체류 시작 시간 설정
     */
    private LocalDateTime calculateStayStartTime(String stayKey, Double newLat, Double newLng) {
        Object stayObj = redisTemplate.opsForValue().get(stayKey);

        if (stayObj instanceof StayInfo previousStay) {
            boolean isSameLocation = GeoUtils.isWithinRadius(
                    previousStay.latitude(), previousStay.longitude(),
                    newLat, newLng,
                    STAY_RADIUS_METERS
            );

            if (isSameLocation) {
                log.debug("같은 위치 유지 - stayKey: {}, 체류 시작: {}", stayKey, previousStay.startTime());
                return previousStay.startTime();
            }
        }

        StayInfo newStay = StayInfo.of(newLat, newLng);
        redisTemplate.opsForValue().set(stayKey, newStay, STAY_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("새로운 위치로 이동 - stayKey: {}, 체류 시작: {}", stayKey, newStay.startTime());

        return newStay.startTime();
    }
}
