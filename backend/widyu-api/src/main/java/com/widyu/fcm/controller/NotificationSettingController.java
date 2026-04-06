package com.widyu.fcm.controller;

import com.widyu.fcm.application.NotificationSettingService;
import com.widyu.fcm.controller.docs.NotificationSettingDocs;
import com.widyu.fcm.dto.request.UpdateNotificationSettingRequest;
import com.widyu.fcm.dto.response.NotificationSettingResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fcm/settings")
@RequiredArgsConstructor
public class NotificationSettingController implements NotificationSettingDocs {

    private final NotificationSettingService notificationSettingService;

    @GetMapping
    public ApiResponseTemplate<List<NotificationSettingResponse>> getNotificationSettings() {
        return ApiResponseTemplate.ok()
                .code("FCM_2010")
                .message("알림 설정 조회 성공")
                .body(notificationSettingService.getNotificationSettings());
    }

    @PatchMapping
    public ApiResponseTemplate<NotificationSettingResponse> updateNotificationSetting(
            @Valid @RequestBody UpdateNotificationSettingRequest request
    ) {
        return ApiResponseTemplate.ok()
                .code("FCM_2011")
                .message("알림 설정 변경 성공")
                .body(notificationSettingService.updateNotificationSetting(request));
    }
}