package com.widyu.heart;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HeartRateFreshness 30초 기준 단위 테스트")
class HeartRateFreshnessTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0, 0);

    @Test
    @DisplayName("측정 후 정확히 30초면 지금의 값으로 본다")
    void 정확히_30초면_신선하다() {
        // given
        LocalDateTime measuredAt = NOW.minusSeconds(30);

        // when
        boolean fresh = HeartRateFreshness.isFresh(measuredAt, NOW);

        // then
        assertThat(fresh).isTrue();
    }

    @Test
    @DisplayName("측정 후 30.5초가 지나면 소수 초를 버리지 않고 오래된 값으로 본다")
    void 삼십점오초가_지나면_오래됐다() {
        // given
        LocalDateTime measuredAt = NOW.minusSeconds(30).minusNanos(500_000_000);

        // when
        boolean fresh = HeartRateFreshness.isFresh(measuredAt, NOW);

        // then
        assertThat(fresh).isFalse();
    }

    @Test
    @DisplayName("측정 시각이 없거나 미래면 지금의 값으로 보지 않는다")
    void 시각이_없거나_미래면_신선하지_않다() {
        // when
        boolean missing = HeartRateFreshness.isFresh(null, NOW);
        boolean future = HeartRateFreshness.isFresh(NOW.plusNanos(1), NOW);

        // then
        assertThat(missing).isFalse();
        assertThat(future).isFalse();
    }
}
