package com.widyu.domain.fcm.api;

import com.widyu.domain.fcm.api.dto.FcmSendDto;
import com.widyu.domain.fcm.api.dto.response.FcmCategoryResponse;
import com.widyu.domain.fcm.api.dto.response.FcmNotificationResponses;
import com.widyu.domain.fcm.api.dto.response.FcmSendResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.util.List;

@Tag(name = "FCM", description = "푸시 메시지 및 알림 API")
public interface FcmDocs {

//    @Operation(
//            summary = "푸시 메시지 전송",
//            description = "현재 로그인한 유저의 모든 디바이스 토큰으로 푸시 메시지를 전송합니다."
//    )
//    @ApiResponse(
//            responseCode = "200",
//            description = "푸시 메시지 전송 성공",
//            content = @Content(
//                    schema = @Schema(implementation = ApiResponseTemplate.class),
//                    examples = @ExampleObject(
//                            value = """
//                                    {
//                                      "code": "FCM_2001",
//                                      "message": "푸시 메시지 전송 성공",
//                                      "data": {
//                                        "title": "새 공지사항",
//                                        "body": "새로운 공지사항이 등록되었습니다.",
//                                        "successCount": 2
//                                      }
//                                    }
//                                    """
//                    )
//            )
//    )
//    ApiResponseTemplate<FcmSendResponse> pushMessage(
//            @RequestBody(
//                    required = true,
//                    description = "푸시 메시지 요청 DTO",
//                    content = @Content(
//                            schema = @Schema(implementation = FcmSendDto.class),
//                            examples = @ExampleObject(
//                                    value = """
//                                            {
//                                              "title": "새 공지사항",
//                                              "body": "새로운 공지사항이 등록되었습니다."
//                                            }
//                                            """
//                            )
//                    )
//            )
//            FcmSendDto fcmSendDto
//    ) throws IOException;

    @Operation(
            summary = "알림 목록 조회",
            description = "현재 로그인한 유저의 알림 목록을 카테고리별 및 커서 기반으로 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 목록 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "200",
                                      "message": "OK",
                                      "data": {
                                        "notifications": [
                                          {
                                            "notificationId": 1,
                                            "image": "~",
                                            "category": "ALBUM",
                                            "title": "부모님이 올려두신 게시물을 모두 읽었어요!",
                                            "content": "새로운 게시물을 업로드해주세요",
                                            "createdAt": "2025-08-26T14:00:00",
                                            "scheme": "gbableappcare://~"
                                          }
                                        ],
                                        "hasNext": true,
                                        "nextCursor": 123
                                      }
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<FcmNotificationResponses> getNotification(String category, Long cursor);

    @Operation(
            summary = "개별 알림 읽음 처리",
            description = "알림 ID를 기준으로 특정 알림을 읽음 상태로 변경합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 읽음 처리 완료",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = {
                            @ExampleObject(
                                    name = "성공 - 읽음 처리",
                                    description = "알림을 성공적으로 읽음 처리한 경우",
                                    value = """
                                            {
                                              "code": "200",
                                              "message": "OK",
                                              "data": "알림 읽음 처리 성공"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "성공 - 이미 읽음",
                                    description = "이미 읽은 알림인 경우",
                                    value = """
                                            {
                                              "code": "200",
                                              "message": "OK",
                                              "data": "이미 읽은 알림입니다"
                                            }
                                            """
                            )
                    }
            )
    )
    ApiResponseTemplate<String> markAsRead(Long notificationId);

    @Operation(
            summary = "알림 카테고리 조회",
            description = "알림 카테고리별 개수를 조회합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 카테고리 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_2005",
                                      "message": "알림 카테고리 조회 성공",
                                      "data": [
                                        {
                                          "label": "ALL",
                                          "name": "전체",
                                          "count": 5
                                        },
                                        {
                                          "label": "ALBUM",
                                          "name": "앨범",
                                          "count": 3
                                        }
                                      ]
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<List<FcmCategoryResponse>> getNotificationCategories();
}
