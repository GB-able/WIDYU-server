package com.widyu.heart;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 최근 30초 안에 잰 심박만 「지금」의 값으로 본다. 워치는 1초마다 보내므로 이보다 오래 끊기면 미착용으로 본다.
 * 홈 카드의 착용 여부, 심박 상태 조회, 그래프 {@code current}가 이 기준을 함께 쓴다(#1023).
 */
public final class HeartRateFreshness {

    private static final Duration CURRENT_WINDOW = Duration.ofSeconds(30);

    private HeartRateFreshness() {
    }

    /** 측정 시각이 없거나 미래이거나 30초보다 오래됐으면 false다. */
    public static boolean isFresh(LocalDateTime measuredAt, LocalDateTime now) {
        if (measuredAt == null) {
            return false;
        }
        // getSeconds()는 소수 초를 버려 30.5초 지난 값도 통과시키므로 Duration끼리 비교한다.
        Duration elapsed = Duration.between(measuredAt, now);
        return !elapsed.isNegative() && elapsed.compareTo(CURRENT_WINDOW) <= 0;
    }
}
