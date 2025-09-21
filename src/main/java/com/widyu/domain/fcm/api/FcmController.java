package com.widyu.domain.fcm.api;

import com.widyu.domain.fcm.api.dto.FcmSendDto;
import com.widyu.domain.fcm.api.dto.response.FcmCategoryResponse;
import com.widyu.domain.fcm.api.dto.response.FcmNotificationResponses;
import com.widyu.domain.fcm.api.dto.response.FcmSendResponse;
import com.widyu.domain.fcm.application.FcmService;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/fcm")
@RequiredArgsConstructor
public class FcmController implements FcmDocs {

    private final FcmService fcmService;

//    @PostMapping()
//    public ApiResponseTemplate<FcmSendResponse> pushMessage(@RequestBody FcmSendDto fcmSendDto) throws IOException {
//        FcmSendResponse response = fcmService.sendMessageTo(fcmSendDto);
//
//        return ApiResponseTemplate.ok()
//                .code("FCM_2001")
//                .message("푸시 메시지 전송 성공")
//                .body(response);
//    }

    @GetMapping()
    public ApiResponseTemplate<FcmNotificationResponses> getNotification() {
        return ApiResponseTemplate.ok()
                .code("FCM_2002")
                .message("사용자 알림 조회 성공")
                .body(fcmService.getNotificationsForCurrentUser());
    }

    @PatchMapping("/{notificationId}")
    public ApiResponseTemplate<String> markAsRead(@PathVariable Long notificationId) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("OK")
                .body(fcmService.markAsRead(notificationId));
    }

    @GetMapping("/categories")
    public ApiResponseTemplate<List<FcmCategoryResponse>> getNotificationCategories() {
        return ApiResponseTemplate.ok()
                .code("FCM_2005")
                .message("알림 카테고리 조회 성공")
                .body(fcmService.getNotificationCategories());
    }
}
