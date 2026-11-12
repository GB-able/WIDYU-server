package com.widyu.goal.rate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

/**
 * Figma 「목표 홈_주간 달성률」 계산법(#1013). 목표 홈 주간·일별, 약 복용·걷기 월별 조회가 함께 쓴다.
 */
public final class GoalRateCalculator {

    private GoalRateCalculator() {
    }

    /**
     * 목표가 있는 항목마다 완수 ÷ 목표를 구해 평균한 정수 퍼센트(반올림). 목표가 있는 항목이 없으면 null.
     * 예: 건강방문 2/3, 의약품 20/30, 걷기 15/30 → (66.7 + 66.7 + 50.0) ÷ 3 = 61.
     */
    public static Integer averagePercent(List<GoalTally> categories) {
        List<GoalTally> targeted = categories.stream()
                .filter(GoalTally::hasTarget)
                .toList();
        if (targeted.isEmpty()) {
            return null;
        }
        // double로 나누면 29/200 = 14.5%가 14.4999…로 내려가 반올림이 틀어진다.
        BigDecimal ratioSum = targeted.stream()
                .map(tally -> BigDecimal.valueOf(Math.min(tally.done(), tally.target()))
                        .divide(BigDecimal.valueOf(tally.target()), MathContext.DECIMAL64))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ratioSum.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(targeted.size()), MathContext.DECIMAL64)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** 한 항목의 정수 퍼센트. 목표가 없으면 null. */
    public static Integer percent(GoalTally tally) {
        return averagePercent(List.of(tally));
    }

    /** 0.0~1.0 비율을 쓰는 기존 응답 필드용. 반올림한 퍼센트를 100으로 나누고, 목표가 없으면 0.0이다. */
    public static double toRatio(Integer percent) {
        if (percent == null) {
            return 0.0;
        }
        return percent / 100.0;
    }

    /**
     * 월 비교 박스의 왼쪽 달. 오른쪽은 항상 이번 달이다.
     * 보는 달이 이번 달이면 지난달, 아니면 보는 달이다.
     */
    public static YearMonth comparisonLeftMonth(YearMonth viewedMonth, YearMonth currentMonth) {
        if (viewedMonth.equals(currentMonth)) {
            return currentMonth.minusMonths(1);
        }
        return viewedMonth;
    }
}
