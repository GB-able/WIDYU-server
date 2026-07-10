package com.widyu.goal.healthschedule.event;

import com.widyu.goal.healthschedule.application.HealthScheduleProgressService;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class HealthScheduleLocationEventListener {

    private final HealthScheduleProgressService healthScheduleProgressService;

    @Async
    @EventListener
    @Transactional
    public void handleSeniorLocationUpdated(SeniorLocationUpdatedEvent event) {
        healthScheduleProgressService.completeArrivedSchedules(
                event.memberId(),
                event.latitude(),
                event.longitude()
        );
    }
}
