package com.widyu.mypage.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.mypage.dto.request.UpdateInviteCodeRequest;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdateSeniorAddressRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.response.ConnectedSeniorResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.FamilyMemberListResponse;
import com.widyu.mypage.dto.response.GuardianInfoResponse;
import com.widyu.mypage.dto.response.GuardianProfileDetailResponse;
import com.widyu.mypage.dto.response.SeniorProfileForGuardianResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianMyPageService {

    private final MemberUtil memberUtil;
    private final S3Service s3Service;
    private final FamilyConnectionRepository familyConnectionRepository;
    private final SeniorProfileRepository seniorProfileRepository;

    public GuardianInfoResponse getGuardianInfo() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        return GuardianInfoResponse.from(member);
    }

    public GuardianProfileDetailResponse getProfileDetail() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        return GuardianProfileDetailResponse.from(member);
    }

    @Transactional
    public void updateName(UpdateNameRequest request) {
        MyPageProfileService.updateCurrentMemberName(memberUtil, request);
    }

    @Transactional
    public void updateProfileImage(MultipartFile image) {
        MyPageProfileService.updateCurrentMemberProfileImage(memberUtil, s3Service, image);
    }

    public ConnectedSeniorResponse getConnectedSeniors() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        List<FamilyConnection> connections = familyConnectionRepository
                .findAllByGuardianIdWithSeniorAndMember(member.getId());
        return ConnectedSeniorResponse.from(connections);
    }

    public SeniorProfileForGuardianResponse getSeniorProfile(Long seniorId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        return SeniorProfileForGuardianResponse.of(seniorProfile.getMember(), seniorProfile);
    }

    public FamilyCodeResponse getFamilyCode() {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        List<FamilyConnection> connections = familyConnectionRepository
                .findAllByGuardianIdWithSeniorAndMember(guardian.getId());
        if (connections.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "연결된 가족이 없습니다.");
        }
        return FamilyCodeResponse.of(connections.get(0).getSenior().getFamilyCode());
    }

    @Transactional
    public void updateSeniorPhone(Long seniorId, UpdatePhoneRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile.getId(), guardian.getId());
        seniorProfile.getMember().updatePhoneNumber(request.phoneNumber());
    }

    @Transactional
    public void updateSeniorAddress(Long seniorId, UpdateSeniorAddressRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile.getId(), guardian.getId());
        seniorProfile.updateAddress(request.address(), request.detailAddress());
    }

    @Transactional
    public void updateSeniorName(Long seniorId, UpdateNameRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile.getId(), guardian.getId());
        seniorProfile.getMember().updateName(request.name());
    }

    @Transactional
    public void updateSeniorProfileImage(Long seniorId, MultipartFile image) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile.getId(), guardian.getId());
        MyPageProfileService.updateMemberProfileImage(s3Service, seniorProfile.getMember(), image);
    }

    @Transactional
    public void updateSeniorInviteCode(Long seniorId, UpdateInviteCodeRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile.getId(), guardian.getId());
        if (seniorProfileRepository.existsByInviteCode(request.inviteCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 사용 중인 초대코드입니다.");
        }
        seniorProfile.updateInviteCode(request.inviteCode());
    }

    public FamilyMemberListResponse getFamilyMembers() {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        List<FamilyConnection> guardianConnections = familyConnectionRepository
                .findAllByGuardianIdWithSeniorAndMember(guardian.getId());
        if (guardianConnections.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "연결된 가족이 없습니다.");
        }
        Long seniorProfileId = guardianConnections.get(0).getSenior().getId();

        List<FamilyConnection> connections = familyConnectionRepository
                .findAllBySeniorIdWithGuardian(seniorProfileId);

        return FamilyMemberListResponse.of(connections, guardian.getId());
    }

    @Transactional
    public void changeLeader(Long seniorId, Long targetGuardianId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());

        if (!familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(
                seniorProfile.getId(), guardian.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 방장을 변경할 수 있습니다.");
        }

        List<FamilyConnection> connections = familyConnectionRepository
                .findAllBySeniorIdWithGuardian(seniorProfile.getId());

        boolean targetFound = connections.stream()
                .anyMatch(c -> c.getGuardian().getId().equals(targetGuardianId));
        if (!targetFound) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다.");
        }

        connections.forEach(c -> c.setLeader(c.getGuardian().getId().equals(targetGuardianId)));
    }

    @Transactional
    public void deleteFamilyMember(Long seniorId, Long targetGuardianId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());

        if (!familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(
                seniorProfile.getId(), guardian.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 멤버를 삭제할 수 있습니다.");
        }

        if (guardian.getId().equals(targetGuardianId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "본인을 삭제할 수 없습니다.");
        }

        FamilyConnection connection = familyConnectionRepository
                .findBySeniorIdAndGuardianId(seniorProfile.getId(), targetGuardianId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다."));

        familyConnectionRepository.delete(connection);
    }

    private void assertIsLeader(Long seniorProfileId, Long guardianId) {
        if (!familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(seniorProfileId, guardianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 수정할 수 있습니다.");
        }
    }

    private SeniorProfile getSeniorProfileWithAccessCheck(Long seniorMemberId, Long guardianId) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(seniorMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!familyConnectionRepository.existsBySeniorIdAndGuardianId(seniorProfile.getId(), guardianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어에 접근 권한이 없습니다.");
        }

        return seniorProfile;
    }
}
