package com.widyu.mypage.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.mypage.dto.request.ProfileImageUploadRequest;
import com.widyu.mypage.dto.request.UpdateInviteCodeRequest;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.request.UpdateSeniorAddressRequest;
import com.widyu.mypage.dto.response.ConnectedSeniorResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.FamilyMemberListResponse;
import com.widyu.mypage.dto.response.GuardianInfoResponse;
import com.widyu.mypage.dto.response.GuardianProfileDetailResponse;
import com.widyu.mypage.dto.response.SeniorProfileForGuardianResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Guardian MyPage", description = "보호자 마이페이지 API")
public interface GuardianMyPageDocs {

    @Operation(summary = "보호자 내 정보 조회", description = "보호자 홈 화면의 프로필 이미지와 이름을 조회합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
    ApiResponseTemplate<GuardianInfoResponse> getGuardianInfo();

    @Operation(summary = "보호자 프로필 설정 조회", description = "이름, 생년월일, 전화번호, 이메일, 소셜 연동 정보를 조회합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
    ApiResponseTemplate<GuardianProfileDetailResponse> getProfileDetail();

    @Operation(summary = "보호자 이름 수정")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "수정 성공")})
    ApiResponseTemplate<Void> updateName(UpdateNameRequest request);

    @Operation(summary = "보호자 프로필 이미지 수정")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "수정 성공")})
    ApiResponseTemplate<Void> updateProfileImage(ProfileImageUploadRequest request);

    @Operation(summary = "연결된 시니어 목록 조회 (위듀 프로필)", description = "보호자와 연결된 시니어 목록을 조회합니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공")})
    ApiResponseTemplate<ConnectedSeniorResponse> getConnectedSeniors();

    @Operation(summary = "시니어 프로필 조회", description = "특정 시니어의 상세 프로필을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "접근 권한 없음")
    })
    ApiResponseTemplate<SeniorProfileForGuardianResponse> getSeniorProfile(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1") Long memberId);

    @Operation(summary = "가족코드 조회", description = "가족코드(6자리)를 조회합니다. 다른 보호자를 가족에 초대할 때 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "연결된 가족 없음")
    })
    ApiResponseTemplate<FamilyCodeResponse> getFamilyCode();

    @Operation(summary = "시니어 이름 수정", description = "방장만 호출 가능. 시니어의 이름을 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "방장만 수정 가능")
    })
    ApiResponseTemplate<Void> updateSeniorName(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1") Long memberId,
            UpdateNameRequest request);

    @Operation(summary = "시니어 전화번호 수정", description = "방장만 호출 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "방장만 수정 가능")
    })
    ApiResponseTemplate<Void> updateSeniorPhone(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1") Long memberId,
            UpdatePhoneRequest request);

    @Operation(summary = "시니어 주소 수정", description = "방장만 호출 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "방장만 수정 가능")
    })
    ApiResponseTemplate<Void> updateSeniorAddress(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1") Long memberId,
            UpdateSeniorAddressRequest request);

    @Operation(summary = "시니어 프로필 이미지 수정", description = "방장만 호출 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "403", description = "방장만 수정 가능")
    })
    ApiResponseTemplate<Void> updateSeniorProfileImage(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1") Long memberId,
            ProfileImageUploadRequest request);

    @Operation(summary = "시니어 초대코드 수정", description = "방장만 호출 가능. 시니어 로그인에 사용되는 7자리 초대코드를 수정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "이미 사용 중인 초대코드"),
            @ApiResponse(responseCode = "403", description = "방장만 수정 가능")
    })
    ApiResponseTemplate<Void> updateSeniorInviteCode(
            @Parameter(description = "시니어 회원 ID", required = true, example = "1") Long memberId,
            UpdateInviteCodeRequest request);

    @Operation(summary = "가족 멤버 목록 조회", description = "현재 로그인한 보호자가 속한 가족의 멤버 목록과 방장 여부를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "연결된 가족 없음")
    })
    ApiResponseTemplate<FamilyMemberListResponse> getFamilyMembers();

    @Operation(summary = "방장 변경", description = "방장만 호출 가능. 다른 보호자를 방장으로 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "403", description = "방장만 변경 가능")
    })
    ApiResponseTemplate<Void> changeLeader(
            @Parameter(description = "새 방장으로 지정할 보호자 회원 ID", required = true, example = "2") Long memberId);

    @Operation(summary = "가족 멤버 삭제", description = "방장만 호출 가능. 특정 보호자를 가족에서 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "방장만 삭제 가능")
    })
    ApiResponseTemplate<Void> deleteFamilyMember(
            @Parameter(description = "삭제할 보호자 회원 ID", required = true, example = "2") Long memberId);
}
