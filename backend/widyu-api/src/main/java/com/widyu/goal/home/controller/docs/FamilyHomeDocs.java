package com.widyu.goal.home.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.home.dto.response.FamilyListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Goal Home", description = "목표 홈 화면 API")
public interface FamilyHomeDocs {

    @Operation(
            summary = "보호자 - 가족 목록 조회",
            description = """
                    보호자가 자신과 연결된 시니어 가족 목록을 조회합니다.

                    **기능:**
                    - 보호자가 초대코드로 연결한 시니어 목록 조회
                    - 각 시니어의 기본 정보 및 연결 정보 반환

                    **권한:**
                    - 보호자 본인만 가능
                    - 시니어는 이 API를 사용할 수 없음

                    **반환 정보:**
                    - 시니어 ID (memberId)
                    - 시니어 이름 (name)
                    - 시니어 프로필 이미지 (profileImage)
                    - 보호자가 설정한 닉네임 (nickname)
                    - 연결된 날짜 (connectedAt)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponseTemplate<FamilyListResponse> getFamilyList();
}
