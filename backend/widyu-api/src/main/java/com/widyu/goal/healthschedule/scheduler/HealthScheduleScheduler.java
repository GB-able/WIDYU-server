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
     * 5분마다 실행: 방문 인증 가능 시간이 지난 UPCOMING 일정을 INCOMPLETE로 변경
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void markOverdueSchedules() {
        healthScheduleProgressService.markOverdueSchedulesAsIncomplete();
    }
}
