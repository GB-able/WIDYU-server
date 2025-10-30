package com.widyu.healthschedule.application;

import com.widyu.healthschedule.dto.request.HealthScheduleCreateRequest;
import com.widyu.healthschedule.dto.request.HealthScheduleUpdateRequest;
import com.widyu.healthschedule.dto.response.HealthScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthScheduleFacadeImpl implements HealthScheduleFacade {

    private final HealthScheduleService healthScheduleService;

    @Override
    public HealthScheduleResponse createHealthSchedule(HealthScheduleCreateRequest request) {
        return healthScheduleService.createHealthSchedule(request);
    }

    @Override
    public HealthScheduleResponse updateHealthSchedule(Long healthScheduleId, HealthScheduleUpdateRequest request) {
        return healthScheduleService.updateHealthSchedule(healthScheduleId, request);
    }

    @Override
    public void deleteHealthSchedule(Long healthScheduleId) {
        healthScheduleService.deleteHealthSchedule(healthScheduleId);
    }
}
