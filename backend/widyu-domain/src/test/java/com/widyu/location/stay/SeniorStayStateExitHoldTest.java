package com.widyu.location.stay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SeniorStayState 이탈 확정 대기 단위 테스트(LLD-0192)")
class SeniorStayStateExitHoldTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 28, 12, 0);
    private static final Duration CONFIRM = Duration.ofSeconds(60);

    @Test
    @DisplayName("처음 밖으로 찍힌 뒤 59초에는 대기를 유지하고 대기 시작 시각도 그대로다")
    void 확정_시간_1초_전에는_대기를_유지한다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0.minusHours(1), "HOME", "집");
        state.holdExit(T0, CONFIRM);

        // when
        boolean held = state.holdExit(T0.plusSeconds(59), CONFIRM);

        // then
        assertThat(held).isTrue();
        assertThat(state.getPendingExitSince()).isEqualTo(T0);
    }

    @Test
    @DisplayName("처음 밖으로 찍힌 뒤 정확히 60초가 되면 대기를 끝내 이탈을 확정한다")
    void 확정_시간이_되면_이탈을_확정한다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0.minusHours(1), "HOME", "집");
        state.holdExit(T0, CONFIRM);

        // when
        boolean held = state.holdExit(T0.plusSeconds(60), CONFIRM);

        // then
        assertThat(held).isFalse();
    }
}
