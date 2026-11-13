package com.widyu.location.realtime.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.GeoUtils;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.location.realtime.dto.LocationFreshness;
import com.widyu.location.realtime.dto.LocationSource;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.StayInfo;
import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.application.SeniorStayStateService;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.ParentLocation;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위치 한 건의 판정 트랜잭션(LLD-0141, LLD-0142): 시니어 확인, 체류와 안전구역 판정, 상태 행 변경, 이탈, 복귀 알림 Outbox.
 * 이 메서드가 돌려줄 때 트랜잭션이 커밋되고 커넥션이 반납된다. Redis 반영과 방송은 호출자가 그 뒤에 한다(LLD-0121).
 *
 * <p>호출자({@link LocationIngestService})는 트랜잭션 없이 부르므로 {@code REQUIRED}가 늘 새 트랜잭션을 연다. 알림 리스너는
 * 동기 {@code @EventListener}로 같은 트랜잭션에 Outbox를 넣어 상태와 함께 커밋, 롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationJudgeService {

    private static final double STAY_RADIUS_METERS = 30.0; // 30m 이내면 같은 위치
    private static final double SAFE_ZONE_RADIUS_METERS = 75.0; // 안전구역 반경 75m (지름 150m)

    private final SeniorProfileRepository seniorProfileRepository;
    private final ParentLocationRepository parentLocationRepository;
    private final SeniorStayStateService stayStateService;
    private final SafeZoneAlertService safeZoneAlertService;

    @Transactional
    public LocationUpdateResponse judge(LocationUpdateRequest request, long receivedAtMs) {
        Long memberId = request.memberId();
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "존재하지 않는 시니어입니다."));
        Member seniorMember = seniorProfile.getMember();

        StayInfo stayInfo = decideStay(request.resolvedLatitude(), request.resolvedLongitude(), seniorMember);

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
     * 체류 정보 계산과 체류, 안전구역 상태 저장(LLD-0141).
     * - 체류 지점에서 30m 이내면 기존 체류를 유지하고 상태 행을 쓰지도 잠그지도 않는다.
     * - 30m 넘게 움직였으면 행을 잠그고 최신 값으로 다시 판정한 뒤 새 체류로 바꾼다.
     * - 안전구역(HOME, OTHER) 75m 반경 내면 해당 타입, 아니면 null.
     * - 안전구역 이탈, 복귀 알림은 상태 행의 30분 묶음으로 정하고, 알림 Outbox는 같은 트랜잭션에 들어간다.
     *
     * <p>같은 회원의 처리는 JVM 순서 잠금이 한 번에 하나씩 보내고, DB 행 잠금은 상태를 바꾸는 순간의 마지막 방어선이다.
     */
    private StayInfo decideStay(Double newLat, Double newLng, Member member) {
        Long memberId = member.getId();
        SeniorStayState current = stayStateService.find(memberId).orElse(null);
        if (isSameStay(current, newLat, newLng)) {
            log.debug("같은 위치 유지 - memberId: {}", memberId);
            return StayInfo.from(current);
        }

        SeniorStayState locked = stayStateService.lockForChange(memberId).orElse(null);
        if (isSameStay(locked, newLat, newLng)) {
            return StayInfo.from(locked);
        }

        ParentLocation matchedZone = findSafeZone(member, newLat, newLng);
        String locationType = locationTypeOf(matchedZone);
        String locationName = locationNameOf(matchedZone);
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
        return StayInfo.from(locked);
    }

    private static boolean isSameStay(SeniorStayState state, Double newLat, Double newLng) {
        return state != null && GeoUtils.isWithinRadius(
                state.getLatitude(), state.getLongitude(), newLat, newLng, STAY_RADIUS_METERS);
    }

    /** 등록된 안전구역(HOME, OTHER) 중 75m 반경 안에 든 첫 구역. 없으면 null. */
    private ParentLocation findSafeZone(Member member, Double lat, Double lng) {
        List<ParentLocation> safeZones = parentLocationRepository.findAllByMember(member);
        for (ParentLocation safeZone : safeZones) {
            if (GeoUtils.isWithinRadius(
                    lat, lng, safeZone.getLatitude(), safeZone.getLongitude(), SAFE_ZONE_RADIUS_METERS)) {
                return safeZone;
            }
        }
        return null;
    }

    private static String locationTypeOf(ParentLocation matchedZone) {
        if (matchedZone == null) {
            return null;
        }
        return matchedZone.getLocationType().name();
    }

    private static String locationNameOf(ParentLocation matchedZone) {
        if (matchedZone == null) {
            return null;
        }
        return matchedZone.getName();
    }
}
