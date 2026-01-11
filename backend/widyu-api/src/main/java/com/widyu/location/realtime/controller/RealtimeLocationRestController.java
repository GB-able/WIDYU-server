package com.widyu.location.realtime.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.global.util.SecurityUtil;
import com.widyu.location.realtime.application.RealtimeLocationService;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/location/realtime")
public class RealtimeLocationRestController {

    private final RealtimeLocationService realtimeLocationService;
    private final SecurityUtil securityUtil;

    /**
     * 보호자가 추적 가능한 시니어 목록 조회 (위치 탭 진입 시)
     */
    @GetMapping("/seniors")
    public ApiResponseTemplate<List<TrackedSeniorResponse>> getTrackedSeniors() {
        Long guardianId = securityUtil.getCurrentMemberId();
        List<TrackedSeniorResponse> response = realtimeLocationService.getTrackedSeniors(guardianId);

        return ApiResponseTemplate.ok()
                .code("LOC_1000")
                .message("추적 가능한 시니어 목록 조회 성공")
                .body(response);
    }

    /**
     * 시니어의 마지막 위치 조회 (초기 로딩용)
     */
    @GetMapping("/senior/{seniorId}")
    public ApiResponseTemplate<LocationUpdateResponse> getLastLocation(
            @PathVariable Long seniorId
    ) {
        Long guardianId = securityUtil.getCurrentMemberId();
        LocationUpdateResponse response = realtimeLocationService.getLastLocation(seniorId, guardianId);

        return ApiResponseTemplate.ok()
                .code("LOC_2000")
                .message("마지막 위치 조회 성공")
                .body(response);
    }

    /**
     * 시니어의 15분 이동 경로 조회
     */
    @GetMapping("/senior/{seniorId}/trail")
    public ApiResponseTemplate<LocationTrailResponse> getLocationTrail(
            @PathVariable Long seniorId
    ) {
        Long guardianId = securityUtil.getCurrentMemberId();
        LocationTrailResponse response = realtimeLocationService.getLocationTrail(seniorId, guardianId);

        return ApiResponseTemplate.ok()
                .code("LOC_2001")
                .message("이동 경로 조회 성공")
                .body(response);
    }
}
