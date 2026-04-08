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

@Tag(name = "FCM Settings", description = "그룹별 알림 수신 설정 API")
public interface NotificationSettingDocs {

    @Operation(
            summary = "알림 설정 조회",
            description = "현재 로그인한 유저의 그룹별 알림 수신 설정을 조회합니다. 그룹 내 하나의 카테고리라도 활성화되어 있으면 그룹은 enabled=true로 반환됩니다."
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
                                          "group": "GOAL",
                                          "groupName": "목표 관련 알림",
                                          "enabled": true
                                        },
                                        {
                                          "group": "ALBUM",
                                          "groupName": "앨범 관련 알림",
                                          "enabled": false
                                        },
                                        {
                                          "group": "HOME",
                                          "groupName": "안전/소통 관련 알림",
                                          "enabled": true
                                        },
                                        {
                                          "group": "ETC",
                                          "groupName": "기타 알림",
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
            description = "특정 그룹의 알림 수신 설정을 변경합니다. enabled=false로 설정하면 해당 그룹에 속한 모든 카테고리의 알림이 전송되지 않습니다."
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
                                        "group": "ALBUM",
                                        "groupName": "앨범 관련 알림",
                                        "enabled": false
                                      }
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 그룹",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_4001",
                                      "message": "유효하지 않은 알림 그룹입니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<NotificationSettingResponse> updateNotificationSetting(
            @RequestBody(
                    description = "알림 설정 변경 요청",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateNotificationSettingRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "그룹 알림 끄기",
                                            value = """
                                                    {
                                                      "group": "ALBUM",
                                                      "enabled": false
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "그룹 알림 켜기",
                                            value = """
                                                    {
                                                      "group": "ALBUM",
                                                      "enabled": true
                                                    }
                                                    """
                                    )
                            }
                    )
            ) UpdateNotificationSettingRequest request
    );
}

