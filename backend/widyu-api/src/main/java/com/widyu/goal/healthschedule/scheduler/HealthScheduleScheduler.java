package com.widyu.goal.healthschedule.scheduler;

import com.widyu.goal.healthschedule.application.HealthScheduleProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HealthScheduleScheduler {

    private final HealthScheduleProgressService healthScheduleProgressService;

    /**
     * 매일 자정에 실행: UPCOMING 상태에서 시간이 지난 일정을 INCOMPLETE로 변경
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void markOverdueSchedules() {
            healthScheduleProgressService.markOverdueSchedulesAsIncomplete();
    }
}