package com.widyu.addressbookmark.controller.docs;

import com.widyu.addressbookmark.dto.request.AddressBookmarkCreateRequest;
import com.widyu.addressbookmark.dto.response.AddressBookmarkResponse;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.member.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Address Bookmark", description = "주소 즐겨찾기 API")
public interface AddressBookmarkDocs {

    @Operation(summary = "주소 즐겨찾기 생성", description = "주소를 즐겨찾기에 추가합니다.")
    @RequestBody(
        description = "주소 정보",
        required = true,
        content = @Content(
            schema = @Schema(implementation = AddressBookmarkCreateRequest.class),
            examples = @ExampleObject(
                value = """
                    {
                      "roadAddress" : "서울특별시 마포구 성암로 301",
                      "address": "서울특별시 마포구 상암동 1595"
                    }
                    """
            )
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
                        "data": {
                            "id": 1,
                            "roadAddress": "서울특별시 마포구 성암로 301",
                            "address": "서울특별시 마포구 상암동 1595"
                        }
                    }
                    """
            )
        )
    )
    ApiResponseTemplate<AddressBookmarkResponse> createAddressBookmark(
        AddressBookmarkCreateRequest request
    );
}
