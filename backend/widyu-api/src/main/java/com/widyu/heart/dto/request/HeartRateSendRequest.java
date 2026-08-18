package com.widyu.heart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HeartRateSendRequest(
        @NotNull(message = "심박수 데이터는 필수입니다.")
        @Size(min = 15, max = 15, message = "심박수 데이터는 정확히 15개여야 합니다.")
        @Valid
        List<HeartRateMeasurement> heartRates,

        String location, // 현재 위치 주소 (이상치 감지 시 위급상황 기록에 사용)

        @Pattern(
                regexp = "^(REST|LOW|ACTIVE|UNKNOWN|\\s*)$",
                message = "활동 상태는 REST, LOW, ACTIVE, UNKNOWN 중 하나여야 합니다."
        )
        String context
) {

    private static final String FALLBACK_CONTEXT = "UNKNOWN";

    public static HeartRateSendRequest of(List<HeartRateMeasurement> heartRates, String location) {
        return new HeartRateSendRequest(heartRates, location, "UNKNOWN");
    }

    public static HeartRateSendRequest of(List<HeartRateMeasurement> heartRates, String location, String context) {
        return new HeartRateSendRequest(heartRates, location, context);
    }

    /**
     * AI에 전달할 활동 상태. 앱이 무엇을 보내든 현재는 항상 {@code UNKNOWN}이다.
     * <p>
     * AI는 {@code context=REST}일 때만 개인 기준선(L1) 경로로 판정하는데, 실측에서 이 경로는 190bpm을
     * 60초 지속시켜도 위급으로 판정하지 않았다(#477). 반면 {@code UNKNOWN}을 포함한 고정 임계값(L0)
     * 경로는 정상적으로 감지한다. 개인화보다 위급 감지를 우선해 L0 경로로 고정한다.
     * <p>
     * 앱이 보낸 원본은 {@link #context()}로 접근할 수 있고 처리 로그의 {@code rawContext}에 남는다.
     */
    public String normalizedContext() {
        // TODO(#477): AI가 L1 경로에서도 위급을 판정하게 되면 아래 정규화 로직으로 되돌린다.
        //  if (context == null || context.isBlank()) return FALLBACK_CONTEXT;
        //  return context;
        return FALLBACK_CONTEXT;
    }
}
