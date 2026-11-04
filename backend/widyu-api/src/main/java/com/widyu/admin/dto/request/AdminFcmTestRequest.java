package com.widyu.admin.dto.request;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.NotificationType;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 관리자 FCM 테스트 발송 요청.
 * {@code notificationType}을 주면 그 종류의 전송 설정으로 보낸다. 데이터 전용이면 표시 문구 없이 보내고,
 * 그 밖에는 제목·본문이 비어 있을 때(null·빈 문자열) 문구표의 정해진 문구로 보낸다. {@code copyCode}가 없으면
 * 서버가 실제로 보내는 OS 푸시 문구를 기본으로 쓴다({@code NotificationCopy.defaultCode}).
 * 문구 변수는 {@code values}로 줄 수 있고, 주지 않은 변수는 "관리자"로 채운다.
 */
public record AdminFcmTestRequest(
        Long memberId,
        @Size(max = 255, message = "알림 제목은 255자 이하로 입력해 주세요.")
        String title,
        @Size(max = 255, message = "알림 내용은 255자 이하로 입력해 주세요.")
        String content,
        FcmCategory category,
        NotificationType notificationType,
        String copyCode,
        Map<String, String> values
) {}
