package com.widyu.goal;

/**
 * 일별 목표 달성 상태
 */
public enum DailyGoalStatus {
    /**
     * 기한 전/오늘, 시작 전
     */
    NOT_STARTED,

    /**
     * 기한 전/오늘, 진행 중
     */
    IN_PROGRESS,

    /**
     * 기한 내 완료
     */
    COMPLETED,

    /**
     * 기한 내 완료 X (기한 지났지만 미완료)
     */
    FAILED
}
