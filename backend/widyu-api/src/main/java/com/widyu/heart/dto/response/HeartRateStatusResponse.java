package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "심박수 이상치 조회 응답")
public record HeartRateStatusResponse(
        @Schema(description = "회원 ID", example = "1023")
        Long memberId,

        @Schema(description = "심박수 상태", example = "NORMAL")
        HeartRateStatus heartRateStatus,

        @Schema(description = "심박수 (BPM)", example = "180")
        Integer bpm,

        @Schema(description = "측정 시각", example = "2026-02-01T15:48:00")
        LocalDateTime measuredAt
) {
    public static HeartRateStatusResponse from(HeartRateResult result) {
        return new HeartRateStatusResponse(
                result.getMemberId(),
                result.getStatus(),
                result.getBpm(),
                result.getMeasuredAt()
        );
    }

    public static HeartRateStatusResponse unknown(Long memberId) {
        return new HeartRateStatusResponse(
                memberId,
                HeartRateStatus.UNKNOWN,
                null,
                null
        );
    }
}
