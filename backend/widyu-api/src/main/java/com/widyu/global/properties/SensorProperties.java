package com.widyu.global.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sensor")
public record SensorProperties(
        int maxPayloadBytes,
        Config config,
        Export export,
        FallAi fallAi,
        HeartAi heartAi,
        Incident incident,
        Followup followup,
        Medication medication
) {

    /**
     * 워치에 내려보내는 수집 설정(지시서 B12, 정책 1.6.4).
     * 참가자별 값이 아니라 실증 프로토콜 단위 운영값이다.
     *
     * <p>{@code defaultCollectionMode}는 열린 회차가 없을 때의 수집 모드다(#989). 운영은 {@code product},
     * 시험을 늘 실험 모드로 하는 개발 서버는 {@code research}로 둔다. 다른 값이면 기동하지 않는다.
     */
    public record Config(
            String gyroMode,
            int accFsHz,
            int batchSec,
            double impactThresholdG,
            int backfillBeforeSec,
            int backfillAfterSec,
            int hrUploadSec,
            int locationMoveSec,
            int locationKeepaliveSec,
            int heartbeatSec,
            int refreshSec,
            String defaultCollectionMode
    ) {

        public Config {
            if (defaultCollectionMode == null) {
                defaultCollectionMode = "product";
            }
            if (!"product".equals(defaultCollectionMode) && !"research".equals(defaultCollectionMode)) {
                throw new IllegalArgumentException(
                        "sensor.config.default-collection-mode는 product 또는 research여야 합니다: "
                                + defaultCollectionMode);
            }
        }
    }

    /** 내보내기 설정(LLD-0050 4절). 값 집합이 미정인 상수(X1·X8·X9)를 여기에 모은다. */
    public record Export(
            long pollDelayMs,
            long runningTimeoutMs,
            int presignMinutes,
            String serverBuild,
            java.util.Map<String, Long> expectedPeriodMs,
            java.util.Map<String, Long> gapThresholdMs,
            Clock clock,
            String absentReasonRoleMissing,
            String absentReasonNoData
    ) {

        public record Clock(
                String sourceDomain,
                String targetDomain,
                String transform,
                String evidenceMethod
        ) {}
    }

    /** 낙상 모델 입구. 모델 배포 전에는 기본값 false로 어떤 호출도 하지 않는다. */
    public record FallAi(
            boolean enabled,
            String path,
            int windowBeforeSec,
            String serverDeciderId,
            String serverDeciderVersion
    ) {}

    /**
     * 심박 판정기의 이름과 판. AI 응답에 판정기 식별자가 없어 설정값으로 적는다(ADR-0035 부정 1).
     * AI 이미지 태그를 올릴 때 손으로 맞춰야 한다.
     *
     * <p>{@code wearGateEnabled}는 착용이 확실하지 않은 심박을 판정하지 않는 스위치다(LLD-0126). 워치가 다시 찬 뒤에도
     * 미착용을 보내는 문제(앱 #109)가 실기기에서 확인되기 전에는 꺼 둔다. 먼저 켜면 정상 착용 심박까지 판정에서 빠진다.
     */
    public record HeartAi(
            String deciderId,
            String deciderVersion,
            boolean wearGateEnabled
    ) {}

    /** 본인확인 마감·순서 전환·상황 창(LLD-0070), 확인 화면 수신 대기(LLD-0085). */
    public record Incident(
            int selfCheckSec,
            long timeoutPollMs,
            boolean selfCheckFirst,
            int situationWindowMin,
            int promptWaitSec
    ) {}

    /** 종료 뒤 카드와 첫 제출 보상. 각각 활성화 전 기본값 false(LLD-0073·0075). */
    public record Followup(boolean enabled, boolean rewardEnabled) {}

    /** 앱 기기 알람 배포 전 정각 서버 푸시를 유지한다(LLD-0080). */
    public record Medication(boolean onTimePush) {}
}
