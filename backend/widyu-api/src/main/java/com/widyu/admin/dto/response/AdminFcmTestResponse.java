package com.widyu.admin.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.widyu.fcm.DeliveryMode;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmTestAttempt;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "관리자 FCM 테스트 발송 결과. 기기 토큰과 Firebase 자격증명은 담지 않는다.")
public record AdminFcmTestResponse(
        Long memberId,
        @Schema(description = "요청한 알림 종류. 종류 없이 보냈으면 null", nullable = true)
        NotificationType notificationType,
        @Schema(description = "그 종류의 업무 전달 방식. 종류 없이 보냈으면 null", nullable = true)
        DeliveryMode deliveryMode,
        @Schema(description = "발송 대상인 활성 토큰 수. 0이면 토큰이 없어 보내지 않았다")
        int tokenCount,
        @Schema(description = "FCM이 요청을 받은 토큰 수. 기기 표시를 보장하지 않는다")
        int sent,
        int failed,
        @Schema(description = "관리자 시간 예산이 남지 않아 호출하지 않은 토큰 수")
        int notAttempted,
        @Schema(description = "결과를 해석할 때 주의할 점")
        List<String> notes,
        List<Attempt> attempts
) {

    @Schema(description = "토큰 하나의 발송 결과")
    public record Attempt(
            @Schema(description = "토큰 행 ID. 기기 토큰 값이 아니다")
            Long tokenId,
            @Schema(description = "SENT, FAILED, NOT_ATTEMPTED")
            FcmTestAttempt.Outcome outcome,
            @Schema(description = "실패·미시도 사유 코드. 예: HTTP_400 INVALID_ARGUMENT, PREFLIGHT, RETRYABLE, "
                    + "TIME_BUDGET_EXHAUSTED", nullable = true)
            String detail,
            @Schema(description = "FCM에 보낸 요청 본문에서 message.token만 뺀 JSON. FCM 응답을 받지 못했으면 null",
                    nullable = true)
            JsonNode payload
    ) {
        public static Attempt from(FcmTestAttempt attempt) {
            return new Attempt(attempt.tokenId(), attempt.outcome(), attempt.detail(), attempt.sentBody());
        }
    }

    public static AdminFcmTestResponse of(Long memberId, NotificationType type, List<FcmTestAttempt> attempts) {
        DeliveryMode mode = null;
        if (type != null) {
            mode = type.deliveryMode();
        }
        return new AdminFcmTestResponse(memberId, type, mode, attempts.size(),
                count(attempts, FcmTestAttempt.Outcome.SENT), count(attempts, FcmTestAttempt.Outcome.FAILED),
                count(attempts, FcmTestAttempt.Outcome.NOT_ATTEMPTED), notes(mode, attempts.isEmpty()),
                attempts.stream().map(Attempt::from).toList());
    }

    private static int count(List<FcmTestAttempt> attempts, FcmTestAttempt.Outcome outcome) {
        return (int) attempts.stream().filter(attempt -> attempt.outcome() == outcome).count();
    }

    private static List<String> notes(DeliveryMode mode, boolean noToken) {
        List<String> notes = new ArrayList<>();
        if (noToken) {
            notes.add("등록된 활성 FCM 토큰이 없어 보내지 않았습니다.");
        }
        notes.add("관리자 테스트는 알림 설정과 알림센터 저장을 거치지 않고 FCM으로 직접 보냅니다. "
                + "실제 수신자, 설정 반영, 센터 저장은 업무 API로 확인해야 합니다.");
        notes.add("SENT는 FCM이 요청을 받았다는 뜻이며 기기에 알림이 표시됐다는 뜻이 아닙니다.");
        if (mode == DeliveryMode.CENTER_ONLY) {
            notes.add("이 종류는 실제 업무에서 알림센터에만 저장하고 푸시를 보내지 않습니다. 이 결과는 전송 형식 확인용입니다.");
        }
        if (mode == DeliveryMode.DATA_ONLY) {
            notes.add("표시 알림 없는 무음 동기화 신호라 기기 화면에 알림이 뜨지 않습니다. 앱 수신 처리로 확인합니다. "
                    + "실제 업무에서는 사용자 알림 설정과 관계없이 보냅니다.");
        }
        return notes;
    }
}
