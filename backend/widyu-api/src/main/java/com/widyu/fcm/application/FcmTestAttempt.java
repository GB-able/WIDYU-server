package com.widyu.fcm.application;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 관리자 테스트 발송의 토큰 하나에 대한 결과. 기기 토큰 대신 토큰 행 ID만 담는다.
 * SENT는 FCM이 요청을 받았다는 뜻이며 기기 표시를 보장하지 않는다.
 */
public record FcmTestAttempt(Long tokenId, Outcome outcome, String detail, JsonNode sentBody) {
    public static final String RETRYABLE = "RETRYABLE";
    public static final String REJECTED = "REJECTED";
    public static final String TIME_BUDGET_EXHAUSTED = "TIME_BUDGET_EXHAUSTED";

    public enum Outcome { SENT, FAILED, NOT_ATTEMPTED }

    static FcmTestAttempt of(Long tokenId, FcmTransport.Result result) {
        if (result.success()) {
            return new FcmTestAttempt(tokenId, Outcome.SENT, null, result.sentBody());
        }
        return new FcmTestAttempt(tokenId, Outcome.FAILED, failureDetail(result), result.sentBody());
    }

    static FcmTestAttempt timeBudgetExhausted(Long tokenId) {
        return new FcmTestAttempt(tokenId, Outcome.NOT_ATTEMPTED, TIME_BUDGET_EXHAUSTED, null);
    }

    // 429·5xx·시간 초과는 상세 코드가 없어 재시도 대상이라는 사실만 남긴다.
    private static String failureDetail(FcmTransport.Result result) {
        if (result.detail() != null) {
            return result.detail();
        }
        if (result.retryable()) {
            return RETRYABLE;
        }
        return REJECTED;
    }
}
