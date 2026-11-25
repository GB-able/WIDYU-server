package com.widyu.fcm.controller.docs;

import com.widyu.fcm.dto.request.DeliveryReceivedRequest;
import com.widyu.fcm.dto.request.SendNotificationRequest;
import com.widyu.fcm.dto.response.FcmCategoryResponse;
import com.widyu.fcm.dto.response.FcmNotificationResponses;
import com.widyu.fcm.dto.response.ToastResDto;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "FCM", description = "푸시 메시지 및 알림 API")
public interface FcmDocs {

    @Operation(
            summary = "알림 목록 조회",
            description = "현재 로그인한 유저의 알림 목록을 탭별, 커서 기반으로 조회합니다. `category`는 카테고리 조회 응답의 `label`(ALL, UNREAD, MESSAGE, GOAL, ALBUM, LOCATION, ETC)입니다. `UNREAD`이면 탭과 관계없이 안 읽은 알림만 반환합니다. 만료된 알림은 빠집니다. 모르는 값이나 시니어의 `LOCATION`은 400(FCM_4002)입니다."
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
                                            "image": "https://cdn.widyu.store/profile/123.png",
                                            "category": "ALBUM",
                                            "title": "부모님이 올려두신 게시물을 모두 읽었어요!",
                                            "content": "새로운 게시물을 업로드해주세요",
                                            "createdAt": "2025-08-26T14:00:00",
                                            "isRead": false,
                                            "path": "/album"
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
            description = "현재 수신자의 알림센터 ID를 읽음 처리합니다. 목록에 저장되는 알림의 FCM data.notificationId는 목록 notificationId와 같은 값인 문자열입니다."
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
            summary = "긴급 알림 폰 도착 신호",
            description = """
                    긴급 알림(심박 위급 1·2차, 본인확인, 워치 도움 요청)을 받은 즉시 앱이 부릅니다. 경로의 `deliveryId`는 FCM data의 `deliveryId`(문자열) 값입니다. 긴급 알림에만 실립니다.

                    - 안드로이드: FCM 백그라운드·포그라운드 메시지 핸들러에서 부릅니다.
                    - 아이폰: Notification Service Extension에서 부릅니다. 긴급 알림의 APNs `aps.mutable-content=1`이라 앱이 꺼져 있어도 확장이 실행됩니다.

                    서버가 신호를 받은 시각을 도착 시각으로 남기고, 본문의 `deviceReceivedAtMs`(기기 시계, 선택)는 참고로만 저장합니다. 같은 건을 다시 보내도 첫 기록이 유지되고 200입니다. 본인에게 간 긴급 알림이 아니면 404입니다.
                    """
    )
    @RequestBody(
            description = "기기 시계 기준 도착 시각(선택). 본문을 생략해도 됩니다.",
            required = false,
            content = @Content(
                    schema = @Schema(implementation = DeliveryReceivedRequest.class),
                    examples = @ExampleObject(value = """
                            {
                              "deviceReceivedAtMs": 1763548800123
                            }
                            """)
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "도착 기록(이미 기록된 경우 포함)",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(value = """
                            {
                              "code": "FCM_2012",
                              "message": "알림 도착을 기록했습니다"
                            }
                            """)
            )
    )
    @ApiResponse(responseCode = "404", description = "본인에게 간 긴급 알림 전송 건이 아님 (FCM_4041)")
    ApiResponseTemplate<Void> markDeliveryReceived(Long deliveryId, DeliveryReceivedRequest request);

    @Operation(
            summary = "알림 카테고리 조회",
            description = "알림 목록 탭과 탭별 안 읽은 알림 개수를 앱에 보여줄 순서대로 조회합니다. 전체, 안 읽음, 메시지, 목표, 앨범, 위치, 기타 순이며 위치는 보호자에게만 옵니다. `ALL`과 `UNREAD`의 count는 둘 다 전체 안 읽은 수입니다."
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
                                          "label": "UNREAD",
                                          "name": "안 읽음",
                                          "count": 5
                                        },
                                        {
                                          "label": "MESSAGE",
                                          "name": "메시지",
                                          "count": 1
                                        },
                                        {
                                          "label": "GOAL",
                                          "name": "목표",
                                          "count": 0
                                        },
                                        {
                                          "label": "ALBUM",
                                          "name": "앨범",
                                          "count": 3
                                        },
                                        {
                                          "label": "LOCATION",
                                          "name": "위치",
                                          "count": 1
                                        },
                                        {
                                          "label": "ETC",
                                          "name": "기타",
                                          "count": 0
                                        }
                                      ]
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<List<FcmCategoryResponse>> getNotificationCategories();

    @Operation(
            summary = "토스트 알림 조회",
            description = """
                    현재 로그인한 보호자의 토스트 알림을 조회합니다. 보여 줄 토스트가 없으면 data가 null입니다.
                    - 어르신이 게시물을 모두 봤을 때: 「{시니어 이름} 님이 새 게시물을 모두 확인했어요!」 / 「새로운 소식을 올려주세요.」
                    - 남은 게시물이 1~6개일 때: 「{시니어}님께서 보실 소식이 N개밖에 남지 않았어요.」 / 「새로운 게시물을 올려주세요.」
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "토스트 알림 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_2006",
                                      "message": "OK",
                                      "data": {
                                        "title": "김아빠 님이 새 게시물을 모두 확인했어요!",
                                        "content": "새로운 소식을 올려주세요.",
                                        "scheme": ""
                                      }
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<ToastResDto> getToastNotification();

    @Operation(
            summary = "상대방에게 응원 알림 전송",
            description = """
                    같은 가족 구성원에게 응원 메시지 알림 발송 요청을 접수합니다. 다른 가족 회원에게는 보낼 수 없습니다(403).
                    업무 커밋 후 비동기로 발송하며, 성공 응답은 FCM 전송 완료나 기기 수신을 보장하지 않습니다.

                    **메시지 길이**: 사람이 보는 글자(이모지 한 개도 한 글자) 기준 50자까지 받습니다. 줄바꿈과 연속 공백은 공백 하나로 정리하며, 정리 뒤 빈 값이거나 50자를 넘으면 400입니다.

                    **잠금화면(FCM notification)**: 원문을 싣지 않습니다. 제목 '{보낸 사람 이름} 님이 응원메시지를 보냈어요!', 부제목 '앱에서 응원메시지를 확인해주세요.', 발신자 프로필 이미지.

                    **앱 안 문구(FCM data)**: `notificationType=CHEER_MESSAGE_RECEIVED`, `deepLink`(빈 문자열, 이동할 화면 없음), `notificationId`, `eventId`, `actorDisplayName`, `inAppTitle`(잠금화면 제목과 같음), `inAppBody`(메시지 미리보기: 40자 이하는 그대로, 넘으면 앞 39자 + '…').

                    **알림센터**: 메시지 탭에 `type=CHEER_MESSAGE_RECEIVED`, 본문은 미리보기로 저장되고 90일 뒤 만료됩니다. 원문은 서버에 저장하지 않습니다.
                    """
    )
    @RequestBody(
            description = "알림 전송 요청 정보",
            required = true,
            content = @Content(
                    schema = @Schema(implementation = SendNotificationRequest.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "receiverId": 123,
                                      "content": "오늘도 좋은 하루 보내세요~"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "알림 발송 요청 접수",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "FCM_2007",
                                      "message": "알림 발송 요청을 접수했습니다",
                                      "data": "알림 발송 요청을 접수했습니다"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(responseCode = "400", description = "메시지가 정리 뒤 빈 값이거나 50자 초과 (REQ_4001)")
    @ApiResponse(responseCode = "403", description = "받는 사람이 보내는 사람과 같은 가족이 아니거나 없는 회원 (AUTH_4030, 두 경우 같은 응답)")
    ApiResponseTemplate<String> sendNotification(SendNotificationRequest sendNotificationRequest);
}
