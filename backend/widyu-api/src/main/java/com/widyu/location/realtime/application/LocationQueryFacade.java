package com.widyu.location.realtime.application;

import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 보호자의 위치 REST 조회를 읽고, 그 읽기 트랜잭션이 끝난 뒤 위치 열람 기록을 남긴다(LLD-0159).
 *
 * <p>트랜잭션을 두지 않는다. 읽기 트랜잭션 안에서 기록(REQUIRES_NEW)을 부르면 요청 하나가 커넥션 두 개를 쥐어,
 * 동시 조회가 풀 크기를 넘으면 기록이 커넥션 대기로 실패하고 WARN만 남긴 채 빠진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationQueryFacade {

    private final LocationQueryService locationQueryService;
    private final LocationAccessLogService locationAccessLogService;

    public List<TrackedSeniorResponse> getTrackedSeniors(Long guardianId) {
        List<TrackedSeniorResponse> seniors = locationQueryService.getTrackedSeniors(guardianId);
        for (TrackedSeniorResponse senior : seniors) {
            recordAccessQuietly(guardianId, senior.memberId(), LocationAccessPath.REST_FAMILY);
        }
        return seniors;
    }

    public LocationUpdateResponse getLastLocation(Long memberId, Long guardianId) {
        LocationUpdateResponse response = locationQueryService.getLastLocation(memberId, guardianId);
        recordAccessQuietly(guardianId, memberId, LocationAccessPath.REST_LAST);
        return response;
    }

    public LocationTrailResponse getLocationTrail(Long memberId, Long guardianId) {
        LocationTrailResponse response = locationQueryService.getLocationTrail(memberId, guardianId);
        recordAccessQuietly(guardianId, memberId, LocationAccessPath.REST_TRAIL);
        return response;
    }

    /**
     * 위치 열람 사실을 남긴다(LLD-0056 5.1). 기록, 통보가 깨져도 위치 조회는 성공해야 하므로
     * 여기서 모든 예외를 삼킨다. 로그에는 좌표를 남기지 않는다.
     */
    private void recordAccessQuietly(Long guardianId, Long seniorMemberId, LocationAccessPath path) {
        try {
            locationAccessLogService.record(guardianId, seniorMemberId, path);
        } catch (Exception e) {
            log.warn("위치 열람 기록 실패: path={}, cause={}", path, e.getClass().getSimpleName());
        }
    }
}
