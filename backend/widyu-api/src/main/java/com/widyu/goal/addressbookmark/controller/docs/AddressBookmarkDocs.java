package com.widyu.goal.addressbookmark.controller.docs;

import com.widyu.goal.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.goal.addressbookmark.dto.response.AddressBookmarkResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Address Bookmark", description = "주소 즐겨찾기 API")
public interface AddressBookmarkDocs {

    @Operation(summary = "주소 즐겨찾기 목록 조회", description = "로그인한 사용자의 모든 주소 즐겨찾기 목록을 조회합니다.")
    @ApiResponse(
        responseCode = "200",
        description = "즐겨찾기 목록 조회 성공",
        content = @Content(
            schema = @Schema(implementation = ApiResponseTemplate.class),
            examples = @ExampleObject(
                value = """
                    {
                        "code": "ADR_2000",
                        "message": "주소 즐겨찾기 목록이 조회되었습니다.",
                        "data": [
                            {
                                "addressBookmarkId": 1,
                                "roadAddress": "서울특별시 마포구 성암로 301",
                                "address": "서울특별시 마포구 상암동 1595",
                                "name": "MBC",
                                "latitude": "37.5789",
                                "longitude": "126.8912",
                                "road": "성암로 301",
                                "jibun": "상암동 1595"
                            }
                        ]
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<List<AddressBookmarkResponse>> getAddressBookmarks();

    @Operation(summary = "주소 즐겨찾기 생성", description = "주소를 즐겨찾기에 추가합니다.")
    @RequestBody(
        description = "주소 정보",
        required = true,
        content = @Content(
            schema = @Schema(implementation = AddressBookmarkCreateRequest.class)
        )
    )
    @ApiResponse(
        responseCode = "200",
        description = "즐겨찾기 생성 성공",
        content = @Content(
            schema = @Schema(implementation = ApiResponseTemplate.class),
            examples = @ExampleObject(
                value = """
                    {
                        "code": "ADR_2001",
                        "message": "주소 즐겨찾기가 생성되었습니다.",
                        "data": null
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<Void> createAddressBookmark(
        AddressBookmarkCreateRequest request
    );

    @Operation(summary = "주소 즐겨찾기 삭제", description = "주소 즐겨찾기를 삭제합니다.")
    @ApiResponse(
        responseCode = "200",
        description = "즐겨찾기 삭제 성공",
        content = @Content(
            schema = @Schema(implementation = ApiResponseTemplate.class),
            examples = @ExampleObject(
                value = """
                    {
                        "code": "ADR_2002",
                        "message": "주소 즐겨찾기가 삭제되었습니다.",
                        "data": null
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<Void> deleteAddressBookmark(
        @PathVariable Long addressBookmarkId
    );
}
