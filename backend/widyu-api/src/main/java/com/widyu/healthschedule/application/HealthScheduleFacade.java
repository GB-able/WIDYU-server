package com.widyu.healthschedule.application;

import com.widyu.healthschedule.dto.request.HealthScheduleCreateRequest;
import com.widyu.healthschedule.dto.request.HealthScheduleUpdateRequest;
import com.widyu.healthschedule.dto.response.HealthScheduleDayResponse;
import com.widyu.healthschedule.dto.response.HealthScheduleResponse;
import java.util.List;

public interface HealthScheduleFacade {

    /**
     * 시니어가 본인 건강 일정 생성
     */
    HealthScheduleResponse createHealthScheduleForMe(HealthScheduleCreateRequest request);

    /**
     * 보호자가 시니어 건강 일정 생성
     */
    HealthScheduleResponse createHealthScheduleForSenior(Long seniorId, HealthScheduleCreateRequest request);

    /**
     * 건강 일정 수정
     */
    HealthScheduleResponse updateHealthSchedule(Long healthScheduleId, HealthScheduleUpdateRequest request);

    /**
     * 건강 일정 삭제 (논리 삭제)
     */
    void deleteHealthSchedule(Long healthScheduleId);

    /**
     * 시니어 본인 건강 일정 캘린더 조회
     */
    List<HealthScheduleDayResponse> getHealthScheduleCalendarForMe(int year, int month);

    /**
     * 보호자가 시니어 건강 일정 캘린더 조회
     */
    List<HealthScheduleDayResponse> getHealthScheduleCalendarForSenior(Long seniorId, int year, int month);
}
