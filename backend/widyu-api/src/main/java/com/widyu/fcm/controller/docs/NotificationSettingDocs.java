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

@Tag(name = "FCM Settings", description = "본인의 그룹별 제품 푸시 설정 API")
public interface NotificationSettingDocs {

    @Operation(
            summary = "알림 설정 조회",
            description = "본인의 제품 푸시 설정을 화면 순서(order)대로 조회합니다. 보호자는 위듀케어 알림 정책서 2절의 스위치 14개를 다섯 묶음(section: LOCATION 위치, GOAL 목표, ALBUM 앨범, MESSAGE 메시지, ETC 기타)으로 받습니다. 시니어는 앨범·목표·홈·기타 4그룹이며 section은 null입니다. 시니어의 네 그룹과 보호자 누구든의 심박 안전(SAFETY), 방장의 안심구역(SAFE_ZONE)은 항상 켜짐, 변경 불가(mandatoryProductPush=true)로 내려갑니다. 기기 상태 unknown은 OS 권한을 알 수 없다는 뜻입니다."
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
                                          "group": "SAFETY",
                                          "groupName": "심박 안전 알림",
                                          "section": "LOCATION",
                                          "order": 1,
                                          "enabled": true,
                                          "recipientEnabled": true,
                                          "productPushEnabled": true,
                                          "mandatoryProductPush": true,
                                          "canEditProductPush": false,
                                          "devicePushStatus": "unknown",
                                          "policyRevision": 4
                                        },
                                        {
                                          "group": "ALBUM_LIKE",
                                          "groupName": "좋아요",
                                          "section": "ALBUM",
                                          "order": 9,
                                          "enabled": false,
                                          "recipientEnabled": true,
                                          "productPushEnabled": false,
                                          "mandatoryProductPush": false,
                                          "canEditProductPush": true,
                                          "devicePushStatus": "unknown",
                                          "policyRevision": 4
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
            description = "로그인한 본인의 제품 푸시만 변경합니다. 보호자는 조회 응답의 group 값 14개(SAFETY, SAFE_ZONE, SENIOR_OK_NOTICE, MEDICATION_CHECK, HEALTH_SCHEDULE, GOAL_ACHIEVED, ALBUM_NEW_POST, ALBUM_COMMENT, ALBUM_LIKE, ALBUM_SENIOR_VIEWED, ALBUM_UPLOAD_COMPLETE, CHEER_MESSAGE, LEADER_CHANGED, SERVICE_NOTICE)를 사용합니다. 예전 GENERAL은 400(FCM_4001)입니다. 시니어의 ALBUM·GOAL·HOME·ETC는 끌 수 없어 켜기는 저장 없이 켜짐을 돌려주고 끄기는 409(FCM_4090)입니다. policyRevision이 최신값과 다르거나, 보호자가 안전(SAFETY) 푸시를 끄거나, 방장이 안심구역 푸시를 끄면 409입니다. 끈 뒤에 접수되는 알림은 푸시와 알림센터 어디에도 남지 않고, 끄기 전에 접수된 알림센터 항목은 그대로 둡니다."
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
                                        "group": "ALBUM_LIKE",
                                        "groupName": "좋아요",
                                        "section": "ALBUM",
                                        "order": 9,
                                        "enabled": false,
                                        "recipientEnabled": true,
                                        "productPushEnabled": false,
                                        "mandatoryProductPush": false,
                                        "canEditProductPush": true,
                                        "devicePushStatus": "unknown",
                                        "policyRevision": 5
                                      }
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 그룹 또는 요청 값",
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
    @ApiResponse(
            responseCode = "409",
            description = "보호자 안전 푸시·방장 안심구역 푸시·시니어 푸시 필수(FCM_4090) 또는 낡은 policyRevision(FCM_4091)"
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
                                                      "group": "ALBUM_LIKE",
                                                      "enabled": false,
                                                      "policyRevision": 4
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "그룹 알림 켜기",
                                            value = """
                                                    {
                                                      "group": "ALBUM_LIKE",
                                                      "enabled": true,
                                                      "policyRevision": 4
                                                    }
                                                    """
                                    )
                            }
                    )
            ) UpdateNotificationSettingRequest request
    );
}
