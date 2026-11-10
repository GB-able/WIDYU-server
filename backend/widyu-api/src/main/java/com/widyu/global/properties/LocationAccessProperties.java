package com.widyu.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 위치 열람 통보의 운영값(LLD-0056 4절, ADR-0036 결정 4).
 *
 * @param immediateCooldownMin 즉시 통보에서 같은 보호자의 반복 조회를 한 건으로 합치는 시간(분).
 *                             기본 0(열람마다 통보, LLD-0130).
 * @param digestCron           정기 요약을 돌리는 cron 식. 기본 "-"(꺼짐, LLD-0130).
 */
@ConfigurationProperties(prefix = "location-access")
public record LocationAccessProperties(
        int immediateCooldownMin,
        String digestCron
) {
}
