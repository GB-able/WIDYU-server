package com.widyu.fcm.controller.docs;

import com.widyu.fcm.dto.request.UpdateNotificationSettingRequest;
import com.widyu.fcm.dto.response.NotificationSettingResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "FCM Settings", description = "카테고리별 알림 수신 설정 API")
public interface NotificationSettingDocs {

    @Operation(
            summary = "알림 설정 조회",
            description = "현재 로그인한 유저의 카테고리별 알림 수신 설정을 조회합니다. 설정하지 않은 카테고리는 기본값 enabled=true로 반환됩니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 설정 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_2010",
                                      "message": "알림 설정 조회 성공",
                                      "data": [
                                        {
                                          "category": "ALBUM",
                                          "categoryName": "앨범",
                                          "enabled": true
                                        },
                                        {
                                          "category": "TARGET",
                                          "categoryName": "응원 메시지",
                                          "enabled": false
                                        },
                                        {
                                          "category": "HEALTH_SCHEDULE",
                                          "categoryName": "방문 일정",
                                          "enabled": true
                                        },
                                        {
                                          "category": "WALK",
                                          "categoryName": "만보계",
                                          "enabled": true
                                        },
                                        {
                                          "category": "MEDICINE_SCHEDULE",
                                          "categoryName": "복약 알림",
                                          "enabled": true
                                        },
                                        {
                                          "category": "HEART_MESSAGE",
                                          "categoryName": "가족 메시지",
                                          "enabled": true
                                        },
                                        {
                                          "category": "SAFE_ZONE",
                                          "categoryName": "안전구역",
                                          "enabled": true
                                        }
                                      ]
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<List<NotificationSettingResponse>> getNotificationSettings();

    @Operation(
            summary = "알림 설정 변경",
            description = "특정 카테고리의 알림 수신 설정을 변경합니다. enabled=false로 설정하면 해당 카테고리의 알림이 전송되지 않습니다. ALL 카테고리는 변경할 수 없습니다."
    )
    @RequestBody(
            description = "알림 설정 변경 요청",
            required = true,
            content = @Content(
                    schema = @Schema(implementation = UpdateNotificationSettingRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "알림 끄기",
                                    description = "앨범 카테고리 알림을 끄는 경우",
                                    value = """
                                            {
                                              "category": "ALBUM",
                                              "enabled": false
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "알림 켜기",
                                    description = "앨범 카테고리 알림을 켜는 경우",
                                    value = """
                                            {
                                              "category": "ALBUM",
                                              "enabled": true
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 설정 변경 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_2011",
                                      "message": "알림 설정 변경 성공",
                                      "data": {
                                        "category": "ALBUM",
                                        "categoryName": "앨범",
                                        "enabled": false
                                      }
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 카테고리",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_4001",
                                      "message": "유효하지 않은 알림 카테고리입니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<NotificationSettingResponse> updateNotificationSetting(UpdateNotificationSettingRequest request);
}
