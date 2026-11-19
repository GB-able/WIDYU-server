package com.widyu.fcm.controller;

import com.widyu.fcm.controller.docs.FcmDocs;
import com.widyu.fcm.dto.request.DeliveryReceivedRequest;
import com.widyu.fcm.dto.request.SendNotificationRequest;
import com.widyu.fcm.dto.response.FcmCategoryResponse;
import com.widyu.fcm.dto.response.FcmNotificationResponses;
import com.widyu.fcm.dto.response.ToastResDto;
import com.widyu.fcm.application.FcmDeliveryReceiptService;
import com.widyu.fcm.application.FcmService;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
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
    private final FcmDeliveryReceiptService deliveryReceiptService;

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

    @PostMapping("/deliveries/{deliveryId}/received")
    public ApiResponseTemplate<Void> markDeliveryReceived(
            @PathVariable Long deliveryId,
            @RequestBody(required = false) DeliveryReceivedRequest request
    ) {
        Long deviceReceivedAtMs = null;
        if (request != null) {
            deviceReceivedAtMs = request.deviceReceivedAtMs();
        }
        deliveryReceiptService.recordReceived(deliveryId, deviceReceivedAtMs);
        return ApiResponseTemplate.ok()
                .code("FCM_2012")
                .message("알림 도착을 기록했습니다")
                .build();
    }

    @GetMapping("/categories")
    public ApiResponseTemplate<List<FcmCategoryResponse>> getNotificationCategories() {
        return ApiResponseTemplate.ok()
                .code("FCM_2005")
                .message("알림 카테고리 조회 성공")
                .body(fcmService.getNotificationCategories());
    }

    @GetMapping("/toast")
    public ApiResponseTemplate<ToastResDto> getToastNotification() {
        return ApiResponseTemplate.ok()
                .code("FCM_2006")
                .message("OK")
                .body(fcmService.getToastNotification());
    }

    @PostMapping("/send")
    public ApiResponseTemplate<String> sendNotification(@Valid @RequestBody SendNotificationRequest sendNotificationRequest) {
    fcmService.sendNotificationToMember(sendNotificationRequest);

        return ApiResponseTemplate.ok()
                .code("FCM_2007")
                .message("알림 발송 요청을 접수했습니다")
                .body("알림 발송 요청을 접수했습니다");
    }
}
