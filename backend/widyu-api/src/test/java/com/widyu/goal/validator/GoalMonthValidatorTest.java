package com.widyu.goal.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("목표 캘린더 월 조회 범위 검사")
class GoalMonthValidatorTest {

    private static YearMonth monthsFromNow(int months) {
        return YearMonth.now(ZoneId.of("Asia/Seoul")).plusMonths(months);
    }

    @ParameterizedTest
    @ValueSource(ints = {-12, 0, 12})
    @DisplayName("이번 달 기준 앞뒤 12개월 안의 달을 검사하면 그 달을 반환한다")
    void 앞뒤_12개월_안의_달은_통과한다(int months) {
        // given
        YearMonth requested = monthsFromNow(months);

        // when
        YearMonth result = GoalMonthValidator.validate(requested.getYear(), requested.getMonthValue(), true);

        // then
        assertThat(result).isEqualTo(requested);
    }

    @ParameterizedTest
    @ValueSource(ints = {-13, 13})
    @DisplayName("이번 달 기준 13개월 떨어진 달을 검사하면 GOAL_MONTH_OUT_OF_RANGE 예외가 발생한다")
    void 앞뒤_13개월_떨어진_달은_예외가_발생한다(int months) {
        // given
        YearMonth requested = monthsFromNow(months);

        // when & then
        assertThatThrownBy(() -> GoalMonthValidator.validate(requested.getYear(), requested.getMonthValue(), true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_MONTH_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("미래를 막은 채 다음 달을 검사하면 GOAL_FUTURE_MONTH_NOT_ALLOWED 예외가 발생한다")
    void 미래를_막으면_다음_달은_예외가_발생한다() {
        // given
        YearMonth nextMonth = monthsFromNow(1);

        // when & then
        assertThatThrownBy(() -> GoalMonthValidator.validate(nextMonth.getYear(), nextMonth.getMonthValue(), false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_FUTURE_MONTH_NOT_ALLOWED);
    }

    @Test
    @DisplayName("미래를 막은 채 이번 달을 검사하면 이번 달을 반환한다")
    void 미래를_막아도_이번_달은_통과한다() {
        // given
        YearMonth thisMonth = monthsFromNow(0);

        // when
        YearMonth result = GoalMonthValidator.validate(thisMonth.getYear(), thisMonth.getMonthValue(), false);

        // then
        assertThat(result).isEqualTo(thisMonth);
    }

    @Test
    @DisplayName("연도가 YearMonth 범위를 넘는 달을 검사하면 GOAL_MONTH_OUT_OF_RANGE 예외가 발생한다")
    void 연도가_너무_크면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> GoalMonthValidator.validate(Integer.MAX_VALUE, 1, true))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GOAL_MONTH_OUT_OF_RANGE);
    }
}
