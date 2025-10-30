package com.widyu.healthschedule.application;

import com.widyu.healthschedule.dto.request.HealthScheduleCreateRequest;
import com.widyu.healthschedule.dto.request.HealthScheduleUpdateRequest;
import com.widyu.healthschedule.dto.response.HealthScheduleResponse;

public interface HealthScheduleFacade {

    /**
     * 건강 일정 생성
     */
    HealthScheduleResponse createHealthSchedule(HealthScheduleCreateRequest request);

    /**
     * 건강 일정 수정
     */
    HealthScheduleResponse updateHealthSchedule(Long healthScheduleId, HealthScheduleUpdateRequest request);

    /**
     * 건강 일정 삭제 (논리 삭제)
     */
    void deleteHealthSchedule(Long healthScheduleId);
}
