package com.widyu.member.controller.docs;

import com.widyu.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.member.dto.response.MemberWithdrawalRequestResponse;
import com.widyu.member.dto.response.SeniorPointsResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Senior", description = "시니어 회원 전용 API - 포인트 조회, 해금 앨범 ID 조회, 탈퇴 요청")
public interface SeniorDocs {

    @Operation(
            summary = "시니어 포인트 조회",
            description = "로그인한 시니어 회원의 남은 포인트를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "포인트 조회 성공"),
            @ApiResponse(responseCode = "403", description = "시니어 회원만 접근 가능"),
            @ApiResponse(responseCode = "404", description = "시니어 프로필을 찾을 수 없음")
    })
    ApiResponseTemplate<SeniorPointsResponse> getLeftPoints();

    @Operation(
            summary = "해금된 앨범 ID 목록 조회",
            description = "시니어 회원이 해금한 앨범과 같은 가족의 시니어가 작성한 활성 앨범의 ID 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "해금된 앨범 ID 조회 성공"),
            @ApiResponse(responseCode = "403", description = "시니어 회원만 접근 가능")
    })
    ApiResponseTemplate<UnlockedAlbumIdsResponse> getUnlockedAlbums();

    @Operation(
            summary = "탈퇴 요청",
            description = """
                    어르신이 앱 설정의 「탈퇴 요청」을 누르면 부릅니다. 요청 본문은 없습니다.
                    서버는 요청만 기록하고 계정은 바로 바뀌지 않습니다. 운영자가 확인해 `dueAt`(요청 시각 + 10일)
                    안에 처리합니다.

                    처리를 기다리는 요청이 이미 있으면 새로 만들지 않고 그 요청을 돌려줍니다.
                    여러 번 눌러도 요청은 하나입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "접수 성공 또는 기존 열린 요청 반환"),
            @ApiResponse(responseCode = "403", description = "시니어 회원만 요청 가능 (AUTH_4030)")
    })
    ApiResponseTemplate<MemberWithdrawalRequestResponse> requestWithdrawal();
}
