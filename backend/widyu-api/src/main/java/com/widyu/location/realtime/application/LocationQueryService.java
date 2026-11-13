package com.widyu.location.realtime.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.SeniorLocation;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.realtime.dto.LocationFreshness;
import com.widyu.location.realtime.dto.LocationPoint;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.stay.application.SeniorStayStateService;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.LocationType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보호자와 홈 화면의 위치 조회(LLD-0142): 가족 인가, 열람 기록(LLD-0056), 신선도를 실은 응답 조립(LLD-0141).
 * 쓰기는 하지 않는다. 열람 기록만 자기 트랜잭션(REQUIRES_NEW)에서 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationQueryService {

    // 이동 경로로 돌려주는 기간. 측정 시각(순서 시각) 기준이다(LLD-0141).
    private static final long TRAIL_WINDOW_MS = Duration.ofMinutes(15).toMillis();

    private final SeniorLocationRepository seniorLocationRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SeniorStayStateService stayStateService;
    private final LocationAccessLogService locationAccessLogService;

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
        SeniorProfile seniorProfile = findAccessibleSenior(memberId, guardianId);

        recordAccessQuietly(guardianId, memberId, LocationAccessPath.REST_LAST);

        Member seniorMember = seniorProfile.getMember();

        // 체류 시작 시각과 위치 종류는 체류 상태(MySQL)에서 읽는다. 없으면 비운다(지금 시각으로 채우지 않는다).
        LocalDateTime stayStartTime = null;
        String locationType = null;
        String locationName = null;
        StayInfo stayInfo = getStayInfo(memberId);
        if (stayInfo != null) {
            stayStartTime = stayInfo.startTime();
            locationType = stayInfo.locationType();
            locationName = stayInfo.locationName();
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

    /**
     * 특정 시니어의 15분 이동 경로 조회
     * @param memberId 시니어의 Member ID
     * @param guardianId 보호자의 Member ID
     */
    public LocationTrailResponse getLocationTrail(Long memberId, Long guardianId) {
        SeniorProfile seniorProfile = findAccessibleSenior(memberId, guardianId);

        recordAccessQuietly(guardianId, memberId, LocationAccessPath.REST_TRAIL);

        Member seniorMember = seniorProfile.getMember();

        List<Object> rawTrail = redisTemplate.opsForList().range(
                LocationProjectionService.trailKey(memberId), 0, LocationProjectionService.TRAIL_MAX_POINTS - 1);

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

    private SeniorProfile findAccessibleSenior(Long memberId, Long guardianId) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));
        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(guardianId, seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어의 위치를 조회할 권한이 없습니다.");
        }
        return seniorProfile;
    }

    private static LocationFreshness freshnessOf(SeniorLocation location, long nowMs) {
        return LocationFreshness.of(location.displayMeasuredAtMs(), location.getReceivedAtMs(),
                LocationSource.LATEST, nowMs);
    }

    /** 조회 API용 체류 정보. 권위 있는 저장소인 MySQL 체류 상태에서 읽는다(LLD-0141). */
    private StayInfo getStayInfo(Long memberId) {
        return stayStateService.find(memberId).map(StayInfo::from).orElse(null);
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

    private static Long getLongOrNull(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return null;
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
}
