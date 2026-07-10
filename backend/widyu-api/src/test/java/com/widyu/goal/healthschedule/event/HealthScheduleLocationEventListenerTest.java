package com.widyu.goal.healthschedule.event;

import static org.mockito.BDDMockito.then;

import com.widyu.goal.healthschedule.application.HealthScheduleProgressService;
import com.widyu.location.realtime.event.SeniorLocationUpdatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthScheduleLocationEventListener 단위 테스트")
class HealthScheduleLocationEventListenerTest {

    @Mock private HealthScheduleProgressService healthScheduleProgressService;

    @InjectMocks private HealthScheduleLocationEventListener listener;

    @Test
    @DisplayName("시니어 위치 업데이트 이벤트를 받으면 방문 일정 자동 완료를 위임한다")
    void 시니어_위치_업데이트_이벤트를_받으면_방문일정_자동완료를_위임한다() {
        // given
        SeniorLocationUpdatedEvent event = new SeniorLocationUpdatedEvent(1L, 37.5, 127.0);

        // when
        listener.handleSeniorLocationUpdated(event);

        // then
        then(healthScheduleProgressService).should()
                .completeArrivedSchedules(1L, 37.5, 127.0);
    }
}
