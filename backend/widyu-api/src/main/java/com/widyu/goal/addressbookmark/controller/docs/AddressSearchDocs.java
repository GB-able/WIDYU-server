package com.widyu.goal.addressbookmark.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.addressbookmark.dto.response.AddressSearchResponse;
import com.widyu.goal.addressbookmark.dto.response.GeocodingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Address Search", description = "주소 검색 API (도로명주소 공공 API)")
public interface AddressSearchDocs {

    @Operation(summary = "주소 검색", description = "키워드로 도로명주소를 검색합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "주소 검색 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(value = """
                            {
                                "code": "200",
                                "message": "주소 검색 성공",
                                "data": {
                                    "addresses": [
                                        {
                                            "roadAddr": "서울특별시 마포구 성암로 301",
                                            "jibunAddr": "서울특별시 마포구 상암동 1595",
                                            "zipNo": "03921",
                                            "bdNm": "MBC",
                                            "siNm": "서울특별시",
                                            "sggNm": "마포구",
                                            "emdNm": "상암동"
                                        }
                                    ],
                                    "totalCount": 1,
                                    "currentPage": 1,
                                    "countPerPage": 10
                                }
                            }
                            """)
            )
    )
    ApiResponseTemplate<AddressSearchResponse> searchAddress(
            @Parameter(description = "검색 키워드", required = true, example = "성암로 301") String keyword,
            @Parameter(description = "페이지 번호 (1부터 시작)", example = "1") int page,
            @Parameter(description = "페이지당 결과 수 (최대 100)", example = "10") int size
    );

    @Operation(summary = "주소 좌표 변환", description = "도로명 주소를 위도·경도로 변환합니다. (Kakao Local API)")
    @ApiResponse(
            responseCode = "200",
            description = "좌표 변환 성공",
            content = @Content(
                    schema = @Schema(implementation = ApiResponseTemplate.class),
                    examples = @ExampleObject(value = """
                            {
                                "code": "200",
                                "message": "좌표 변환 성공",
                                "data": {
                                    "latitude": "37.5666103",
                                    "longitude": "126.9783882"
                                }
                            }
                            """)
            )
    )
    ApiResponseTemplate<GeocodingResponse> geocodeAddress(
            @Parameter(description = "변환할 주소", required = true, example = "서울특별시 마포구 성암로 301") String address
    );
}
