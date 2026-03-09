package com.widyu.member.controller.docs;

import com.widyu.member.dto.request.FamilyJoinRequest;
import com.widyu.member.dto.response.FamilyJoinResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Guardian", description = "보호자 전용 API")
public interface GuardianDocs {

    @Operation(
            summary = "초대코드로 가족 참여",
            description = "시니어의 7자리 초대코드를 입력하여 가족에 참여합니다. 다른 보호자가 공유한 초대코드를 사용할 수 있습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "가족 참여 성공"),
            @ApiResponse(responseCode = "400", description = "이미 해당 가족에 연결되어 있음"),
            @ApiResponse(responseCode = "403", description = "보호자 회원만 접근 가능"),
            @ApiResponse(responseCode = "404", description = "초대코드를 찾을 수 없음")
    })
    ApiResponseTemplate<FamilyJoinResponse> joinFamily(FamilyJoinRequest request);
}
