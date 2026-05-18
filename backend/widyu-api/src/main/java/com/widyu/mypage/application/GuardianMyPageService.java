package com.widyu.mypage.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
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
    private final FamilyMembershipRepository familyMembershipRepository;
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
        Family family = getGuardianFamily(member.getId());
        List<SeniorProfile> seniors = seniorProfileRepository.findAllByFamilyIdWithMember(family.getId());
        return ConnectedSeniorResponse.from(seniors);
    }

    public SeniorProfileForGuardianResponse getSeniorProfile(Long seniorId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        return SeniorProfileForGuardianResponse.of(seniorProfile.getMember(), seniorProfile);
    }

    public FamilyCodeResponse getFamilyCode() {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        Family family = getGuardianFamily(guardian.getId());
        return FamilyCodeResponse.of(family.getFamilyCode());
    }

    @Transactional
    public void updateSeniorPhone(Long seniorId, UpdatePhoneRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        seniorProfile.getMember().updatePhoneNumber(request.phoneNumber());
    }

    @Transactional
    public void updateSeniorAddress(Long seniorId, UpdateSeniorAddressRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        seniorProfile.updateAddress(request.address(), request.detailAddress());
    }

    @Transactional
    public void updateSeniorName(Long seniorId, UpdateNameRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        seniorProfile.getMember().updateName(request.name());
    }

    @Transactional
    public void updateSeniorProfileImage(Long seniorId, MultipartFile image) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        MyPageProfileService.updateMemberProfileImage(s3Service, seniorProfile.getMember(), image);
    }

    @Transactional
    public void updateSeniorInviteCode(Long seniorId, UpdateInviteCodeRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        if (seniorProfileRepository.existsByInviteCode(request.inviteCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 사용 중인 초대코드입니다.");
        }
        seniorProfile.updateInviteCode(request.inviteCode());
    }

    public FamilyMemberListResponse getFamilyMembers() {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        Family family = getGuardianFamily(guardian.getId());

        List<FamilyMembership> memberships = familyMembershipRepository
                .findAllByFamilyIdWithGuardian(family.getId());
        List<SeniorProfile> seniors = seniorProfileRepository.findAllByFamilyIdWithMember(family.getId());

        return FamilyMemberListResponse.of(memberships, seniors, guardian.getId());
    }

    @Transactional
    public void changeLeader(Long seniorId, Long targetGuardianId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());

        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileIdAndIsLeaderTrue(
                guardian.getId(), seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 방장을 변경할 수 있습니다.");
        }

        List<FamilyMembership> memberships = familyMembershipRepository
                .findAllByFamilyIdWithGuardian(seniorProfile.getFamily().getId());

        boolean targetFound = memberships.stream()
                .anyMatch(m -> m.getGuardian().getId().equals(targetGuardianId));
        if (!targetFound) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다.");
        }

        memberships.forEach(m -> m.setLeader(m.getGuardian().getId().equals(targetGuardianId)));
    }

    @Transactional
    public void deleteFamilyMember(Long seniorId, Long targetGuardianId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(seniorId, guardian.getId());

        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileIdAndIsLeaderTrue(
                guardian.getId(), seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 멤버를 삭제할 수 있습니다.");
        }

        if (guardian.getId().equals(targetGuardianId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "본인을 삭제할 수 없습니다.");
        }

        FamilyMembership membership = familyMembershipRepository
                .findByFamilyIdAndGuardianId(seniorProfile.getFamily().getId(), targetGuardianId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다."));

        familyMembershipRepository.delete(membership);
    }

    private void assertIsLeader(SeniorProfile seniorProfile, Long guardianId) {
        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileIdAndIsLeaderTrue(
                guardianId, seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 수정할 수 있습니다.");
        }
    }

    private Family getGuardianFamily(Long guardianId) {
        return familyMembershipRepository.findByGuardianId(guardianId)
                .map(FamilyMembership::getFamily)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "연결된 가족이 없습니다."));
    }

    private SeniorProfile getSeniorProfileWithAccessCheck(Long seniorMemberId, Long guardianId) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(seniorMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(guardianId, seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어에 접근 권한이 없습니다.");
        }

        return seniorProfile;
    }
}
