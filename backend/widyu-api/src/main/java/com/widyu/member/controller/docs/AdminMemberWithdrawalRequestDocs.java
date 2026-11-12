package com.widyu.member.controller.docs;

import com.widyu.admin.dto.response.AdminPageResponse;
import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.member.MemberWithdrawalRequestStatus;
import com.widyu.member.dto.response.MemberWithdrawalRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "Admin Member Withdrawal Request", description = "어르신 탈퇴 요청 처리 기록 API (운영자)")
public interface AdminMemberWithdrawalRequestDocs {

    @Operation(
            summary = "탈퇴 요청 목록",
            description = """
                    어르신이 앱에서 남긴 탈퇴 요청을 오래된 순서로 봅니다. `status`를 빼면 전부입니다.
                    처리할 요청은 `status=REQUESTED`로 거르고 `dueAt`(요청 시각 + 10일) 전에 처리합니다.
                    이름·전화번호는 담지 않습니다. `memberId`로 회원 상세를 엽니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ApiResponseTemplate<AdminPageResponse<MemberWithdrawalRequestResponse>> getRequests(
            @Parameter(description = "REQUESTED / PROCESSED / CANCELLED, 생략하면 전부")
            MemberWithdrawalRequestStatus status,
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @Min(value = 1, message = "size는 1 이상 100 이하여야 합니다.")
            @Max(value = 100, message = "size는 1 이상 100 이하여야 합니다.") int size);

    @Operation(
            summary = "탈퇴 요청 처리 완료 기록",
            description = """
                    운영자가 어르신 계정 처리를 마친 뒤 부릅니다. **이 API는 계정을 바꾸지 않습니다.**
                    상태를 `PROCESSED`로 바꾸고 처리 시각과 처리 관리자를 남기며 감사 로그를 함께 남깁니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "기록 성공")
    @ApiResponse(
            responseCode = "404",
            description = "없는 요청",
            content = @Content(examples = @ExampleObject(value = """
                    {"code": "MEMBER_4043", "message": "탈퇴 요청을 찾을 수 없습니다.", "data": null}
                    """))
    )
    @ApiResponse(
            responseCode = "409",
            description = "이미 처리 완료되거나 취소된 요청",
            content = @Content(examples = @ExampleObject(value = """
                    {"code": "MEMBER_4091", "message": "이미 처리 완료되거나 취소된 탈퇴 요청입니다.", "data": null}
                    """))
    )
    ApiResponseTemplate<MemberWithdrawalRequestResponse> complete(Long requestId);

    @Operation(
            summary = "탈퇴 요청 취소 기록",
            description = """
                    본인 확인 중 어르신이 탈퇴하지 않겠다고 하면 부릅니다. 상태를 `CANCELLED`로 바꾸고
                    처리 시각과 처리 관리자, 감사 로그를 남깁니다. 404·409 조건은 처리 완료와 같습니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "기록 성공")
    @ApiResponse(responseCode = "404", description = "없는 요청 (MEMBER_4043)")
    @ApiResponse(responseCode = "409", description = "이미 처리 완료되거나 취소된 요청 (MEMBER_4091)")
    ApiResponseTemplate<MemberWithdrawalRequestResponse> cancel(Long requestId);
}
