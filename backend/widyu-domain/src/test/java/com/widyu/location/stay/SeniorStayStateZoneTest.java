package com.widyu.location.stay;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SeniorStayState 안전구역 상태가 바뀐 시각 단위 테스트")
class SeniorStayStateZoneTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 27, 18, 0);

    @Test
    @DisplayName("처음 상태를 만들면 구역 상태가 바뀐 시각은 지금이다")
    void 처음_만들면_구역_시각은_지금이다() {
        // when
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0, "HOME", "집");

        // then
        assertThat(state.getZoneChangedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("안전구역 밖에서 30m 넘게 여러 번 움직이면 체류 시작은 바뀌지만 구역 상태가 바뀐 시각은 나간 시각 그대로다")
    void 밖에서_여러_번_움직여도_나간_시각이_유지된다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0, "HOME", "집");
        state.moveTo(37.51, 127.0, T0.plusMinutes(2), null, null, true);

        // when
        state.moveTo(37.52, 127.0, T0.plusMinutes(30), null, null, true);
        state.moveTo(37.53, 127.0, T0.plusHours(3), null, null, true);

        // then
        assertThat(state.getStayStartedAt()).isEqualTo(T0.plusHours(3));
        assertThat(state.getZoneChangedAt()).isEqualTo(T0.plusMinutes(2));
    }

    @Test
    @DisplayName("집 구역 안에서 30m 넘게 움직이면 집에 들어온 시각이 그대로다")
    void 집_안에서_움직여도_들어온_시각이_유지된다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0, "HOME", "집");

        // when
        state.moveTo(37.5004, 127.0, T0.plusHours(3), "HOME", "집", true);

        // then
        assertThat(state.getStayStartedAt()).isEqualTo(T0.plusHours(3));
        assertThat(state.getZoneChangedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("밖에서 집으로 들어오면 구역 상태가 바뀐 시각을 들어온 시각으로 바꾼다")
    void 밖에서_집으로_들어오면_새로_센다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.6, 127.0, T0, null, null);

        // when
        state.moveTo(37.5, 127.0, T0.plusHours(1), "HOME", "집", true);

        // then
        assertThat(state.getZoneChangedAt()).isEqualTo(T0.plusHours(1));
    }

    @Test
    @DisplayName("다른 안전구역에서 이름이 다른 안전구역으로 바로 옮기면 구역이 바뀐 것으로 보고 새로 센다")
    void 다른_안전구역으로_옮기면_새로_센다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0, "OTHER", "병원");

        // when
        state.moveTo(37.501, 127.0, T0.plusMinutes(40), "OTHER", "경로당", true);

        // then
        assertThat(state.getZoneChangedAt()).isEqualTo(T0.plusMinutes(40));
    }

    @Test
    @DisplayName("안전구역을 하나도 등록하지 않았으면 30m 넘게 움직일 때마다 예전처럼 새로 센다")
    void 안전구역이_없으면_움직일_때마다_새로_센다() {
        // given
        SeniorStayState state = SeniorStayState.start(1L, 37.5, 127.0, T0, null, null);

        // when
        state.moveTo(37.51, 127.0, T0.plusMinutes(20), null, null, false);

        // then
        assertThat(state.getZoneChangedAt()).isEqualTo(T0.plusMinutes(20));
    }

    @Test
    @DisplayName("예전 Redis 상태를 옮겨 오면 구역 상태가 바뀐 시각은 체류 시작 시각이다")
    void 옮겨_온_상태는_체류_시작으로_채운다() {
        // when
        SeniorStayState state = SeniorStayState.imported(1L, 37.5, 127.0, T0, "HOME", "집", null, null);

        // then
        assertThat(state.getZoneChangedAt()).isEqualTo(T0);
    }
}
