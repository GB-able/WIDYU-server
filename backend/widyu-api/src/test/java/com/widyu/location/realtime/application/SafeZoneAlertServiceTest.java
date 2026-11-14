package com.widyu.location.realtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.widyu.fcm.event.safezone.dto.SafeZoneEnterEvent;
import com.widyu.fcm.event.safezone.dto.SafeZoneExitEvent;
import com.widyu.location.stay.SafeZoneAlertState;
import com.widyu.location.stay.SeniorStayState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** 안전구역 알림 30분 묶음 규칙(ADR-0039)이 MySQL 체류 상태로 옮긴 뒤에도 같은지 본다(LLD-0141). */
@ExtendWith(MockitoExtension.class)
@DisplayName("안전구역 이탈, 복귀 알림 묶음")
class SafeZoneAlertServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private SafeZoneAlertService safeZoneAlertService;

    @Test
    @DisplayName("첫 안심구역 이탈이면 30분 묶음을 열고 Z01 이벤트를 한 번 발행한다")
    void 첫_이탈이면_묶음을_열고_Z01을_발행한다() {
        // given
        SeniorStayState state = state();

        // when
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", null, NOW);

        // then
        assertThat(state.getAlertState()).isEqualTo(SafeZoneAlertState.EXITED);
        assertThat(state.getAlertUntil()).isEqualTo(NOW.plusMinutes(30));
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneExitEvent.class));
    }

    @Test
    @DisplayName("30분 묶음 안에서 다시 이탈하면 Z01을 더 발행하지 않는다")
    void 묶음_안에서_재이탈하면_Z01을_더_발행하지_않는다() {
        // given
        SeniorStayState state = state();
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", null, NOW);
        safeZoneAlertService.handleSafeZoneTransition(state, null, "HOME", NOW.plusMinutes(5));

        // when
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", null, NOW.plusMinutes(10));

        // then
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneExitEvent.class));
        assertThat(state.getAlertState()).isEqualTo(SafeZoneAlertState.RETURNED);
        assertThat(state.getAlertUntil()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    @DisplayName("묶음 안에서 처음 돌아오면 EXITED를 RETURNED로 바꾸고 Z02 이벤트를 한 번 발행한다")
    void 묶음_안에서_처음_돌아오면_Z02를_발행한다() {
        // given
        SeniorStayState state = state();
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", null, NOW);

        // when
        safeZoneAlertService.handleSafeZoneTransition(state, null, "HOME", NOW.plusMinutes(5));
        safeZoneAlertService.handleSafeZoneTransition(state, null, "HOME", NOW.plusMinutes(6));

        // then
        then(eventPublisher).should(times(1)).publishEvent(any(SafeZoneEnterEvent.class));
        assertThat(state.getAlertState()).isEqualTo(SafeZoneAlertState.RETURNED);
    }

    @Test
    @DisplayName("이탈 알림 묶음이 없는데 안심구역에 들어오면 돌아옴 소식을 만들지 않는다")
    void 묶음이_없으면_돌아와도_Z02를_만들지_않는다() {
        // given
        SeniorStayState state = state();

        // when
        safeZoneAlertService.handleSafeZoneTransition(state, null, "HOME", NOW);

        // then
        assertThat(state.getAlertState()).isNull();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("묶음이 끝난 뒤 돌아오면 Z02를 만들지 않고, 그 뒤 이탈하면 새 묶음을 열어 Z01을 다시 발행한다")
    void 묶음이_끝나면_복귀는_조용하고_다음_이탈은_다시_알린다() {
        // given
        SeniorStayState state = state();
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", null, NOW);

        // when
        safeZoneAlertService.handleSafeZoneTransition(state, null, "HOME", NOW.plusMinutes(31));
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", null, NOW.plusMinutes(40));

        // then
        then(eventPublisher).should(never()).publishEvent(any(SafeZoneEnterEvent.class));
        then(eventPublisher).should(times(2)).publishEvent(any(SafeZoneExitEvent.class));
        assertThat(state.getAlertUntil()).isEqualTo(NOW.plusMinutes(70));
    }

    @Test
    @DisplayName("위치가 계속 같은 쪽이거나 안전구역끼리 옮기면 상태와 이벤트를 바꾸지 않는다")
    void 같은_쪽이면_상태와_이벤트를_바꾸지_않는다() {
        // given
        SeniorStayState state = state();

        // when
        safeZoneAlertService.handleSafeZoneTransition(state, null, null, NOW);
        safeZoneAlertService.handleSafeZoneTransition(state, "HOME", "OTHER", NOW);

        // then
        assertThat(state.getAlertState()).isNull();
        then(eventPublisher).should(never()).publishEvent(any());
    }

    private static SeniorStayState state() {
        return SeniorStayState.start(1L, 37.5, 127.0, NOW.minusHours(1), "HOME", "집");
    }
}
