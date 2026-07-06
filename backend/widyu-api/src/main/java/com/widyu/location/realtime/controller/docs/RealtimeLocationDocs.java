package com.widyu.location.realtime.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.location.realtime.dto.LocationTrailResponse;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.dto.TrackedSeniorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Realtime-Location", description = "실시간 위치 추적 API")
public interface RealtimeLocationDocs {

    @Operation(
            summary = "추적 가능한 시니어 목록 조회",
            description = "보호자가 추적할 수 있는 시니어 목록을 조회합니다. 위치 탭 진입 시 호출합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "시니어 목록 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                    {
                      "code": "LOC_1000",
                      "message": "추적 가능한 시니어 목록 조회 성공",
                      "data": [
                        {
                          "memberId": 1,
                          "name": "김시니어",
                          "profileImage": "https://example.com/profile1.jpg",
                          "latitude": 37.5665,
                          "longitude": 126.9780
                        },
                        {
                          "memberId": 2,
                          "name": "박시니어",
                          "profileImage": "https://example.com/profile2.jpg",
                          "latitude": null,
                          "longitude": null
                        }
                      ]
                    }
                    """
                    )
            )
    )
    ApiResponseTemplate<List<TrackedSeniorResponse>> getTrackedSeniors();

    @Operation(
            summary = "시니어 마지막 위치 조회",
            description = """
                    시니어의 마지막 위치와 체류 정보를 조회합니다.

                    **체류 시간**: 같은 위치(30m 반경 내)에 머문 시간을 분 단위로 제공합니다.

                    **위치 타입 (locationType)**:
                    - `HOME`: 등록된 집 안심구역 내
                    - `OTHER`: 등록된 기타 안심구역 내
                    - `null`: 등록된 안심구역 밖

                    **위치 이름 (locationName)**:
                    - 안심구역 내에 있을 때 해당 장소의 이름 (예: "집", "병원")
                    - 안심구역 밖이면 `null`
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "마지막 위치 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                    {
                      "code": "LOC_2000",
                      "message": "마지막 위치 조회 성공",
                      "data": {
                        "memberId": 1,
                        "name": "김시니어",
                        "profileImage": "https://example.com/profile1.jpg",
                        "latitude": 37.5665,
                        "longitude": 126.9780,
                        "updatedAt": "2024-01-12T14:30:00",
                        "stayStartTime": "2024-01-12T13:45:00",
                        "stayDurationMinutes": 45,
                        "locationType": "HOME",
                        "locationName": "집"
                      }
                    }
                    """
                    )
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
                      "message": "접근 권한이 없습니다. - 해당 시니어의 위치를 조회할 권한이 없습니다.",
                      "data": null
                    }
                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "위치 정보 없음",
            content = @Content(
                    examples = @ExampleObject(
                            value = """
                    {
                      "code": "SRV_4040",
                      "message": "찾을 수 없습니다 - 최근 위치 정보가 없습니다.",
                      "data": null
                    }
                    """
                    )
            )
    )
    ApiResponseTemplate<LocationUpdateResponse> getLastLocation(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1")
            Long memberId
    );

    @Operation(
            summary = "시니어 이동 경로 조회",
            description = """
                    시니어의 최근 15분간 이동 경로를 조회합니다.

                    지도에 이동 경로를 선으로 그릴 때 사용합니다.
                    시간순으로 정렬되어 반환됩니다.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "이동 경로 조회 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(
                            value = """
                    {
                      "code": "LOC_2001",
                      "message": "이동 경로 조회 성공",
                      "data": {
                        "memberId": 1,
                        "name": "김시니어",
                        "profileImage": "https://example.com/profile1.jpg",
                        "trail": [
                          {
                            "latitude": 37.5663,
                            "longitude": 126.9778,
                            "timestamp": "2024-01-12T14:25:00"
                          },
                          {
                            "latitude": 37.5664,
                            "longitude": 126.9779,
                            "timestamp": "2024-01-12T14:27:00"
                          },
                          {
                            "latitude": 37.5665,
                            "longitude": 126.9780,
                            "timestamp": "2024-01-12T14:30:00"
                          }
                        ],
                        "totalPoints": 3
                      }
                    }
                    """
                    )
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
                      "message": "접근 권한이 없습니다. - 해당 시니어의 위치를 조회할 권한이 없습니다.",
                      "data": null
                    }
                    """
                    )
            )
    )
    ApiResponseTemplate<LocationTrailResponse> getLocationTrail(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1")
            Long memberId
    );
}
