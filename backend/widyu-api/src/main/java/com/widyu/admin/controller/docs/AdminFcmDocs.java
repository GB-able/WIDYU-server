package com.widyu.admin.controller.docs;

import com.widyu.admin.dto.request.AdminFcmTestRequest;
import com.widyu.admin.dto.response.AdminFcmTestResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "관리자 FCM 테스트", description = "local·dev 전용. 특정 회원의 활성 토큰으로 테스트 푸시를 보냅니다.")
public interface AdminFcmDocs {

    @Operation(summary = "FCM 테스트 발송",
            description = """
                    notificationType을 주면 그 종류의 실제 전송 설정(Android 채널·우선순위, APNs 헤더·소리)으로 보냅니다.
                    응답의 attempts[].payload는 FCM에 실제로 보낸 요청 본문에서 기기 토큰(message.token)만 뺀 JSON입니다.
                    토큰마다 SENT(FCM 수락), FAILED(거부·일시 오류·발송 직전 자격 탈락), NOT_ATTEMPTED(관리자 시간 예산 소진)로 나뉘고,
                    tokenCount가 0이면 활성 토큰이 없어 보내지 않은 것입니다.
                    알림 설정과 알림센터를 거치지 않으므로, 알림센터 전용(CENTER_ONLY) 종류도 강제로 푸시됩니다. 실제 업무 발송 여부는 업무 API로 확인합니다.""")
    @ApiResponse(responseCode = "200", description = "발송을 시도했습니다. 토큰별 결과는 attempts를 봅니다.")
    @ApiResponse(responseCode = "400", description = "종류와 맞지 않는 문구 코드이거나 요청 형식이 잘못됐습니다.")
    @ApiResponse(responseCode = "404", description = "회원이 없습니다.")
    ApiResponseTemplate<AdminFcmTestResponse> sendTestNotification(@Valid @RequestBody AdminFcmTestRequest request);
}
