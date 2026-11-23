package com.widyu.member.controller.docs;

import com.widyu.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.member.dto.response.SeniorPointsResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Senior", description = "시니어 회원 전용 API - 포인트 조회, 해금 앨범 ID 조회, 탈퇴")
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
            summary = "어르신 본인 탈퇴 (즉시, 되돌릴 수 없음)",
            description = """
                    로그인된 어르신 앱에서 탈퇴를 확정하면 부릅니다. 요청 본문은 없습니다. 유예 기간 없이 바로 처리합니다.
                    앱은 부르기 전에 확인 화면 두 번을 보여 줍니다(「탈퇴하면 알림이 멈추고 가족이 더 이상 볼 수 없어요.
                    되돌릴 수 없어요」 → 큰 「탈퇴할게요」 버튼).

                    한 트랜잭션에서 가족 보호자 모두에게 「○○ 님이 위듀를 탈퇴하셔서 안부 알림이 멈췄어요」 알림을 접수하고,
                    가족 연결을 끊고, 위치 열람 기록과 위치 원본을 지우고, 이름과 번호를 가린 뒤 `INACTIVE`로 바꿉니다.
                    커밋 뒤 이 어르신의 실시간 연결(STOMP)을 닫습니다. 이후 이 회원의 토큰으로 오는 요청과 재연결은
                    비활성 회원으로 거절됩니다. 나머지 개인 자료는 30일 뒤 지웁니다(LLD-0186).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 완료 (SENIOR_2003, body 없음)"),
            @ApiResponse(responseCode = "403", description = "시니어 회원만 가능 (AUTH_4030)")
    })
    ApiResponseTemplate<Void> withdraw();
}
