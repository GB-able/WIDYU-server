package com.widyu.heart.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.heart.dto.request.HeartMessageRequest;
import com.widyu.heart.dto.response.HeartGraphPageResponse;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Heart Rate", description = "심박수 관련 API")
public interface HeartRateDocs {

    @Operation(
            summary = "심박수 이상치 조회",
            description = """
                    회원의 가장 최신 심박수 분석 결과를 조회합니다.

                    **접근 권한**:
                    - memberId 미입력 시: 본인의 심박수 조회
                    - memberId 입력 시: 가족 관계가 있는 경우에만 조회 가능

                    **HeartRateStatus 상태값**:
                    - `NORMAL`: 정상
                    - `ANOMALY`: 비정상 (이상치 감지)
                    - `UNKNOWN`: 판별 불가 (데이터 없음)
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = {
                            @ExampleObject(
                                    name = "정상 심박수",
                                    value = """
                                            {
                                              "code": "HEART_2001",
                                              "message": "심박수 이상치 조회 완료",
                                              "data": {
                                                "memberId": 1023,
                                                "heartRateStatus": "NORMAL",
                                                "heartRate": 82,
                                                "measuredAt": "2026-02-01T15:48:00"
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "이상 심박수",
                                    value = """
                                            {
                                              "code": "HEART_2001",
                                              "message": "심박수 이상치 조회 완료",
                                              "data": {
                                                "memberId": 1023,
                                                "heartRateStatus": "ANOMALY",
                                                "heartRate": 180,
                                                "measuredAt": "2026-02-01T15:48:00"
                                              }
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "데이터 없음",
                                    value = """
                                            {
                                              "code": "HEART_2001",
                                              "message": "심박수 이상치 조회 완료",
                                              "data": {
                                                "memberId": 1023,
                                                "heartRateStatus": "UNKNOWN",
                                                "heartRate": null,
                                                "measuredAt": null
                                              }
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "권한 없음",
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "AUTH_4030",
                                      "message": "접근 권한이 없습니다. - 가족으로 연결된 시니어만 접근할 수 있습니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<HeartRateStatusResponse> getHeartRateStatus(
            @Parameter(description = "조회할 회원 ID (미입력 시 본인)", example = "1023")
            Long memberId
    );

    @Operation(
            summary = "심박수 그래프 최초 조회",
            description = """
                    심박수 그래프 화면 최초 진입 시 호출합니다.
                    현재 심박수, 오늘의 최대/최소, 최초 이상치 탐지 정보, 전체 이벤트, 위급상황 히스토리를 반환합니다.

                    **접근 권한**:
                    - memberId 미입력 시: 본인 조회
                    - memberId 입력 시: 가족 연결된 경우에만 조회 가능
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ApiResponseTemplate<HeartGraphPageResponse> getHeartGraph(
            @Parameter(description = "조회할 회원 ID (미입력 시 본인)", example = "1023")
            Long memberId
    );

    @Operation(
            summary = "심박수 그래프 갱신",
            description = """
                    심박수 그래프를 갱신할 때 호출합니다.
                    현재 심박수, 최대/최소, 최근 5개 이벤트, 위급상황 히스토리를 반환합니다.

                    **접근 권한**:
                    - memberId 미입력 시: 본인 조회
                    - memberId 입력 시: 가족 연결된 경우에만 조회 가능
                    """
    )
    @ApiResponse(responseCode = "200", description = "갱신 성공")
    ApiResponseTemplate<HeartGraphPageResponse> getHeartGraphRefresh(
            @Parameter(description = "조회할 회원 ID (미입력 시 본인)", example = "1023")
            Long memberId
    );

    @Operation(
            summary = "가족 메시지 전송",
            description = """
                    같은 가족에게 50자 이내의 메시지를 FCM 알림으로 전송합니다.
                    알림은 수신자의 알림 내역에 저장됩니다.

                    **접근 권한**:
                    - 보호자 → 부모님, 부모님 → 보호자 방향 모두 가능
                    - 반드시 가족 연결이 되어 있어야 합니다.
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "메시지 전송 요청",
            required = true,
            content = @Content(
                    schema = @Schema(implementation = HeartMessageRequest.class),
                    examples = @ExampleObject(
                            name = "메시지 전송 예시",
                            value = """
                                    {
                                      "receiverId": 1001,
                                      "message": "오늘 건강은 좀 어때요?"
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "전송 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "HEART_2003",
                                      "message": "메시지 전송 완료",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "잘못된 요청",
            content = @Content(
                    examples = {
                            @ExampleObject(
                                    name = "메시지 초과",
                                    value = """
                                            {
                                              "code": "REQ_4000",
                                              "message": "메시지는 50자 이내로 입력해주세요.",
                                              "data": null
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "필수값 누락",
                                    value = """
                                            {
                                              "code": "REQ_4000",
                                              "message": "받는 사람 ID는 필수입니다.",
                                              "data": null
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponse(
            responseCode = "403",
            description = "가족 관계 없음",
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "AUTH_4030",
                                      "message": "접근 권한이 없습니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "code": "MEMBER_4041",
                                      "message": "회원을 찾을 수 없습니다.",
                                      "data": null
                                    }
                                    """
                    )
            )
    )
    ApiResponseTemplate<Void> sendHeartMessage(HeartMessageRequest request);
}
