package com.widyu.member.controller.docs;

import com.widyu.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.member.dto.response.SeniorPointsResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Senior", description = "시니어 회원 전용 API - 포인트 조회, 해금 앨범 ID 조회")
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
}
