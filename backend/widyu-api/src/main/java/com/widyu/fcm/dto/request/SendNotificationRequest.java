package com.widyu.fcm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendNotificationRequest(
        @NotNull(message = "받는 사람 ID는 필수입니다.")
        Long receiverId,

        @NotBlank(message = "알림 내용은 필수입니다.")
        // 50자(그래핌) 제한은 FcmSendDto.ofMessage가 정리 뒤에 센다. 저장 칸(255)에는 미리보기만 들어가 따로 맞춘다.
        // 여기 상한은 UTF-16 단위라 가족 이모지 50자(약 550)도 받을 만큼 넉넉하게 두고, 큰 본문 남용만 막는다.
        @Size(max = 2000, message = "메시지는 50자 이하로 입력해 주세요.")
        String content
) {
}
