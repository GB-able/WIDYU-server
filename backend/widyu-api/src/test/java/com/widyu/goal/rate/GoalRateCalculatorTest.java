package com.widyu.goal.rate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("GoalRateCalculator 항목별 평균 달성률 단위 테스트")
class GoalRateCalculatorTest {

    @Test
    @DisplayName("건강방문 2/3, 의약품 20/30, 걷기 15/30이면 항목별 달성률 평균 61을 반환한다")
    void Figma_예시는_61퍼센트다() {
        // given
        List<GoalTally> categories = List.of(
                GoalTally.of(2, 3),
                GoalTally.of(20, 30),
                GoalTally.of(15, 30));

        // when
        Integer rate = GoalRateCalculator.averagePercent(categories);

        // then: (66.7 + 66.7 + 50.0) ÷ 3 = 61.1
        assertThat(rate).isEqualTo(61);
    }

    @Test
    @DisplayName("목표가 없는 항목은 평균에서 빼고 남은 항목으로만 평균한다")
    void 목표가_없는_항목은_평균에서_뺀다() {
        // given: 건강방문 일정 없음
        List<GoalTally> categories = List.of(
                GoalTally.NONE,
                GoalTally.of(20, 30),
                GoalTally.of(15, 30));

        // when
        Integer rate = GoalRateCalculator.averagePercent(categories);

        // then: 0%로 끼우면 39가 된다
        assertThat(rate).isEqualTo(58);
    }

    @Test
    @DisplayName("모든 항목에 목표가 없으면 null을 반환하고 기존 비율 필드에는 0.0으로 내린다")
    void 목표가_하나도_없으면_null이다() {
        // when
        Integer rate = GoalRateCalculator.averagePercent(List.of(GoalTally.NONE, GoalTally.NONE));

        // then
        assertThat(rate).isNull();
        assertThat(GoalRateCalculator.toRatio(rate)).isZero();
    }

    @ParameterizedTest(name = "{0}/{1} → {2}")
    @CsvSource({
            "2, 3, 67",      // 66.67 올림
            "1, 3, 33",      // 33.33 내림
            "29, 200, 15",   // 14.5 — double 나눗셈이면 14.4999…로 14가 된다
            "1, 8, 13",      // 12.5
            "3, 2, 100"      // 완수가 목표를 넘어도 100에서 멈춘다
    })
    @DisplayName("달성률은 정수 퍼센트로 반올림한다")
    void 달성률은_정수_퍼센트로_반올림한다(long done, long target, int expected) {
        // when
        Integer rate = GoalRateCalculator.percent(GoalTally.of(done, target));

        // then
        assertThat(rate).isEqualTo(expected);
    }

    @Test
    @DisplayName("기존 비율 필드는 반올림한 퍼센트를 100으로 나눈 값이다")
    void 기존_비율_필드는_반올림한_퍼센트를_쓴다() {
        // when
        double ratio = GoalRateCalculator.toRatio(GoalRateCalculator.percent(GoalTally.of(2, 3)));

        // then
        assertThat(ratio).isEqualTo(0.67);
    }

    @Test
    @DisplayName("보는 달이 이번 달이면 비교 왼쪽은 지난달이다")
    void 보는_달이_이번_달이면_왼쪽은_지난달이다() {
        // when
        YearMonth left = GoalRateCalculator.comparisonLeftMonth(YearMonth.of(2026, 1), YearMonth.of(2026, 1));

        // then: 연도가 바뀌어도 지난달
        assertThat(left).isEqualTo(YearMonth.of(2025, 12));
    }

    @ParameterizedTest
    @CsvSource({"2026, 9", "2026, 10", "2026, 12"})
    @DisplayName("보는 달이 이번 달이 아니면 비교 왼쪽은 보는 달이다")
    void 보는_달이_이번_달이_아니면_왼쪽은_보는_달이다(int year, int month) {
        // given
        YearMonth viewed = YearMonth.of(year, month);

        // when
        YearMonth left = GoalRateCalculator.comparisonLeftMonth(viewed, YearMonth.of(2026, 11));

        // then
        assertThat(left).isEqualTo(viewed);
    }
}
