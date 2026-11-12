package com.widyu.goal.rate;

/**
 * 한 목표 항목의 완수 수와 목표 수. 달성률 계산의 단위다(#1013).
 * 의약품은 복용 횟수, 건강방문은 방문 횟수, 걷기는 달성한 날 수(월 진행률에서는 걸음 수)로 센다.
 */
public record GoalTally(long done, long target) {

    public static final GoalTally NONE = new GoalTally(0, 0);

    public static GoalTally of(long done, long target) {
        return new GoalTally(done, target);
    }

    public GoalTally plus(GoalTally other) {
        return new GoalTally(done + other.done, target + other.target);
    }

    public boolean hasTarget() {
        return target > 0;
    }

    /** 목표가 있고 모두 완수했는지. */
    public boolean isComplete() {
        return hasTarget() && done >= target;
    }
}
