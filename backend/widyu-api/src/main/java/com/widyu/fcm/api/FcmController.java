package com.widyu.fcm.api;

import com.widyu.fcm.api.dto.response.FcmCategoryResponse;
import com.widyu.fcm.api.dto.response.FcmNotificationResponses;
import com.widyu.fcm.api.dto.response.ToastResDto;
import com.widyu.fcm.application.FcmService;
import com.widyu.global.response.ApiResponseTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/fcm")
@RequiredArgsConstructor
public class FcmController implements FcmDocs {

    private final FcmService fcmService;

    @GetMapping()
    public ApiResponseTemplate<FcmNotificationResponses> getNotification(
            @RequestParam(required = false, defaultValue = "ALL") String category,
            @RequestParam(required = false) Long cursor
    ) {
        return ApiResponseTemplate.ok()
                .code("200")
                .message("OK")
                .body(fcmService.getNotificationsForCurrentUser(category, cursor));
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
                .message("OK")
                .body(fcmService.getNotificationCategories());
    }

    @GetMapping("/toast")
    public ApiResponseTemplate<ToastResDto> getToastNotification() {
        return ApiResponseTemplate.ok()
                .code("FCM_2006")
                .message("OK")
                .body(fcmService.getToastNotification());
    }
}
