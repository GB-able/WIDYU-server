package com.widyu.location.parentlocation.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.location.parentlocation.dto.request.ParentLocationCreateRequest;
import com.widyu.location.parentlocation.dto.response.ParentLocationResponse;
import com.widyu.location.parentlocation.dto.response.SeniorWithLocationsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Tag(name = "Parent-Location", description = "부모님 장소 관리 API")
public interface ParentLocationDocs {

    @Operation(
        summary = "부모님 장소 등록",
        description = "시니어 회원의 자주 가는 장소를 등록합니다. 장소 타입은 HOME(집) 또는 OTHER(기타)로 구분됩니다."
    )
    @RequestBody(
        description = "부모님 장소 등록 정보",
        required = true,
        content = @Content(
            schema = @Schema(implementation = ParentLocationCreateRequest.class),
            examples = @ExampleObject(
                value = """
                    {
                      "memberId": 1,
                      "locationType": "HOME",
                      "placeAddress": "서울특별시 강남구 테헤란로 123",
                      "latitude": "37.5012",
                      "longitude": "127.0396"
                    }
                    """
            )
        )
    )
    @ApiResponse(
        responseCode = "200",
        description = "부모님 장소 등록 성공",
        content = @Content(
            schema = @Schema(implementation = ApiResponseTemplate.class),
            examples = @ExampleObject(
                value = """
                    {
                      "code": "PLO_2001",
                      "message": "부모님 장소가 등록되었습니다.",
                      "data": null
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<Void> createParentLocation(ParentLocationCreateRequest request);

    @Operation(
        summary = "부모님 장소 삭제",
        description = "등록된 부모님 장소를 삭제합니다. 소프트 삭제 방식으로 상태만 변경됩니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "부모님 장소 삭제 성공",
        content = @Content(
            schema = @Schema(implementation = ApiResponseTemplate.class),
            examples = @ExampleObject(
                value = """
                    {
                      "code": "PLO_2002",
                      "message": "부모님 장소가 삭제되었습니다.",
                      "data": null
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<Void> deleteParentLocation(
        @Parameter(description = "시니어 회원 ID", required = true, example = "1")
        Long memberId,
        @Parameter(description = "삭제할 부모님 장소 ID", required = true, example = "1")
        Long parentLocationId
    );

    @Operation(
        summary = "부모님 장소 목록 조회",
        description = "보호자가 관리하는 모든 부모님들의 등록된 장소 목록을 부모님별로 그룹핑하여 조회합니다."
    )
    @ApiResponse(
        responseCode = "200",
        description = "부모님 장소 목록 조회 성공",
        content = @Content(
            schema = @Schema(implementation = ApiResponseTemplate.class),
            examples = @ExampleObject(
                value = """
                    {
                      "code": "PLO_2000",
                      "message": "부모님 장소 목록이 조회되었습니다.",
                      "data": [
                        {
                          "memberId": 1,
                          "memberName": "홍길동",
                          "locations": [
                            {
                              "parentLocationId": 1,
                              "locationType": "HOME",
                              "placeAddress": "서울특별시 강남구 테헤란로 123",
                              "latitude": "37.5012",
                              "longitude": "127.0396"
                            },
                            {
                              "parentLocationId": 2,
                              "locationType": "OTHER",
                              "placeAddress": "서울특별시 마포구 월드컵북로 123",
                              "latitude": "37.5665",
                              "longitude": "126.9780"
                            }
                          ]
                        },
                        {
                          "memberId": 2,
                          "memberName": "김영희",
                          "locations": [
                            {
                              "parentLocationId": 3,
                              "locationType": "HOME",
                              "placeAddress": "서울특별시 송파구 올림픽로 456",
                              "latitude": "37.5145",
                              "longitude": "127.1059"
                            }
                          ]
                        }
                      ]
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<List<SeniorWithLocationsResponse>> getParentLocations(
        @Parameter(description = "보호자 회원 ID", required = true, example = "1")
        Long guardianId
    );
}
