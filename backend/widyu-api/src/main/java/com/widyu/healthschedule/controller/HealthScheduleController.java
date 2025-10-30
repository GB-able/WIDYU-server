package com.widyu.healthschedule.controller;

import com.widyu.healthschedule.application.HealthScheduleFacade;
import com.widyu.healthschedule.dto.request.HealthScheduleCreateRequest;
import com.widyu.healthschedule.dto.request.HealthScheduleUpdateRequest;
import com.widyu.healthschedule.dto.response.HealthScheduleResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/health-schedules")
public class HealthScheduleController {

    private final HealthScheduleFacade healthScheduleFacade;

    @PostMapping
    public ApiResponseTemplate<HealthScheduleResponse> createHealthSchedule(
            @Valid @RequestBody HealthScheduleCreateRequest request
    ) {
        HealthScheduleResponse response = healthScheduleFacade.createHealthSchedule(request);

        return ApiResponseTemplate.ok()
                .code("HLTH_2001")
                .message("건강 일정이 생성되었습니다.")
                .body(response);
    }

    @PatchMapping("/{healthScheduleId}")
    public ApiResponseTemplate<HealthScheduleResponse> updateHealthSchedule(
            @PathVariable Long healthScheduleId,
            @Valid @RequestBody HealthScheduleUpdateRequest request
    ) {
        HealthScheduleResponse response = healthScheduleFacade.updateHealthSchedule(healthScheduleId, request);

        return ApiResponseTemplate.ok()
                .code("HLTH_2002")
                .message("건강 일정이 수정되었습니다.")
                .body(response);
    }

    @DeleteMapping("/{healthScheduleId}")
    public ApiResponseTemplate<Void> deleteHealthSchedule(
            @PathVariable Long healthScheduleId
    ) {
        healthScheduleFacade.deleteHealthSchedule(healthScheduleId);

        return ApiResponseTemplate.ok()
                .code("HLTH_2003")
                .message("건강 일정이 삭제되었습니다.")
                .build();
    }
}