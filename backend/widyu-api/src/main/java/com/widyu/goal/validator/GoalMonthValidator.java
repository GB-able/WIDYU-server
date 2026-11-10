package com.widyu.goal.validator;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 목표 캘린더(약 복용·걷기 월간, 건강 일정 캘린더)가 조회할 수 있는 달의 범위(#1005).
 * 이번 달(한국 시간) 기준 앞뒤 12개월까지다. 미래 쪽 "일정이 있는 달까지"는 앱이 화면 이동으로 막는다.
 */
public final class GoalMonthValidator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_MONTHS_FROM_NOW = 12;

    private GoalMonthValidator() {
    }

    /** 범위 밖이면 400. allowFuture가 false면 이번 달 이후도 400이다. */
    public static YearMonth validate(int year, int month, boolean allowFuture) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.GOAL_MONTH_OUT_OF_RANGE);
        }
        YearMonth current = YearMonth.now(KST);
        // YearMonth.of는 연도 범위를 넘으면 DateTimeException(500)이라 월 수로 먼저 비교한다.
        long offset = (year * 12L + month) - (current.getYear() * 12L + current.getMonthValue());
        if (offset < -MAX_MONTHS_FROM_NOW || offset > MAX_MONTHS_FROM_NOW) {
            throw new BusinessException(ErrorCode.GOAL_MONTH_OUT_OF_RANGE);
        }
        if (!allowFuture && offset > 0) {
            throw new BusinessException(ErrorCode.GOAL_FUTURE_MONTH_NOT_ALLOWED);
        }
        return YearMonth.of(year, month);
    }
}
