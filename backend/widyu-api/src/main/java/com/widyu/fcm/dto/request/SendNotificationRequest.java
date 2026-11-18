package com.widyu.fcm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendNotificationRequest(
        @NotNull(message = "받는 사람 ID는 필수입니다.")
        Long receiverId,

        @NotBlank(message = "알림 내용은 필수입니다.")
        // 50자(그래핌) 제한은 FcmSendDto.ofMessage가 정리 뒤에 센다. 255는 저장 칸(VARCHAR 255) 보호용 상한이다.
        @Size(max = 255, message = "메시지는 50자 이하로 입력해 주세요.")
        String content
) {
}
