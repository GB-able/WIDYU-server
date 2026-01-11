package com.widyu.location.realtime.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.global.util.SecurityUtil;
import com.widyu.location.realtime.application.RealtimeLocationService;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
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
     * 시니어의 1시간 이동 경로 조회
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
