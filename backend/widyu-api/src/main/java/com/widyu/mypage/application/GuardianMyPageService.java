package com.widyu.mypage.application;

import com.widyu.auth.PhoneChangeVerified;
import com.widyu.auth.application.SmsService;
import com.widyu.auth.dto.request.SeniorSignUpRequest;
import com.widyu.auth.dto.request.SmsCodeRequest;
import com.widyu.auth.repository.PhoneChangeVerifiedRepository;
import com.widyu.auth.repository.VerificationCodeRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
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

    private static final long PHONE_CHANGE_VERIFIED_TTL_SECONDS = 300;

    private final MemberUtil memberUtil;
    private final S3Service s3Service;
    private final SmsService smsService;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PhoneChangeVerifiedRepository phoneChangeVerifiedRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberRepository memberRepository;
    private final PointHistoryRepository pointHistoryRepository;

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

    @Transactional
    public void addSenior(SeniorSignUpRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        Family family = getGuardianFamily(guardian.getId());

        if (memberRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 사용 중인 전화번호입니다.");
        }

        Member seniorMember = Member.createMember(MemberType.SENIOR, request.name(), request.phoneNumber());
        memberRepository.save(seniorMember);

        SeniorProfile profile = SeniorProfile.createSeniorProfile(
                seniorMember, family,
                null, request.detailAddress(),
                request.inviteCode(), request.birthDate()
        );
        seniorProfileRepository.save(profile);
    }

    @Transactional
    public void sendPhoneChangeSms(UpdatePhoneRequest request) {
        if (memberRepository.findByPhoneNumber(request.phoneNumber()).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 사용 중인 전화번호입니다.");
        }
        Member currentMember = MyPageProfileService.getCurrentMember(memberUtil);
        smsService.sendVerificationSms(request.phoneNumber(), currentMember.getName());
    }

    @Transactional
    public void verifyPhoneChangeCode(SmsCodeRequest request) {
        String newPhone = request.phoneNumber();

        boolean codeMatches = verificationCodeRepository.findById(newPhone)
                .map(v -> v.getCode().equals(request.code()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SMS_VERIFICATION_CODE_NOT_FOUND));

        if (!codeMatches) {
            throw new BusinessException(ErrorCode.SMS_VERIFICATION_CODE_MISMATCH);
        }

        verificationCodeRepository.deleteById(newPhone);
        phoneChangeVerifiedRepository.save(PhoneChangeVerified.builder()
                .phoneNumber(newPhone)
                .ttl(PHONE_CHANGE_VERIFIED_TTL_SECONDS)
                .build());
    }

    @Transactional
    public void updatePhone(UpdatePhoneRequest request) {
        String newPhone = request.phoneNumber();

        phoneChangeVerifiedRepository.findById(newPhone)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "전화번호 인증이 완료되지 않았습니다."));

        Member currentMember = MyPageProfileService.getCurrentMember(memberUtil);
        currentMember.updatePhoneNumber(newPhone);
        phoneChangeVerifiedRepository.deleteById(newPhone);
    }

    public ConnectedSeniorResponse getConnectedSeniors() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        Family family = getGuardianFamily(member.getId());
        List<SeniorProfile> seniors = seniorProfileRepository.findAllByFamilyIdWithMember(family.getId());
        return ConnectedSeniorResponse.from(seniors);
    }

    public SeniorProfileForGuardianResponse getSeniorProfile(Long memberId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(memberId, guardian.getId());
        return SeniorProfileForGuardianResponse.of(seniorProfile.getMember(), seniorProfile);
    }

    public FamilyCodeResponse getFamilyCode() {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        Family family = getGuardianFamily(guardian.getId());
        return FamilyCodeResponse.of(family.getFamilyCode());
    }

    @Transactional
    public void updateSeniorPhone(Long memberId, UpdatePhoneRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(memberId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        seniorProfile.getMember().updatePhoneNumber(request.phoneNumber());
    }

    @Transactional
    public void updateSeniorAddress(Long memberId, UpdateSeniorAddressRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(memberId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        seniorProfile.updateAddress(request.address(), request.detailAddress());
    }

    @Transactional
    public void updateSeniorName(Long memberId, UpdateNameRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(memberId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        seniorProfile.getMember().updateName(request.name());
    }

    @Transactional
    public void updateSeniorProfileImage(Long memberId, MultipartFile image) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(memberId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
        MyPageProfileService.updateMemberProfileImage(s3Service, seniorProfile.getMember(), image);
    }

    @Transactional
    public void updateSeniorInviteCode(Long memberId, UpdateInviteCodeRequest request) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = getSeniorProfileWithAccessCheck(memberId, guardian.getId());
        assertIsLeader(seniorProfile, guardian.getId());
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
    public void changeLeader(Long targetMemberId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        FamilyMembership myMembership = familyMembershipRepository.findByGuardianId(guardian.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "연결된 가족이 없습니다."));

        if (!myMembership.isLeader()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 방장을 변경할 수 있습니다.");
        }

        List<FamilyMembership> memberships = familyMembershipRepository
                .findAllByFamilyIdWithGuardian(myMembership.getFamily().getId());

        boolean targetFound = memberships.stream()
                .anyMatch(m -> m.getGuardian().getId().equals(targetMemberId));
        if (!targetFound) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다.");
        }

        memberships.forEach(m -> m.setLeader(m.getGuardian().getId().equals(targetMemberId)));
    }

    @Transactional
    public void deleteFamilyMember(Long targetMemberId) {
        Member guardian = MyPageProfileService.getCurrentMember(memberUtil);
        FamilyMembership myMembership = familyMembershipRepository.findByGuardianId(guardian.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "연결된 가족이 없습니다."));

        if (!myMembership.isLeader()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 멤버를 삭제할 수 있습니다.");
        }

        if (guardian.getId().equals(targetMemberId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "본인을 삭제할 수 없습니다.");
        }

        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (target.getType() == MemberType.SENIOR) {
            deleteSeniorFromFamily(targetMemberId, myMembership.getFamily().getId());
        } else {
            deleteGuardianFromFamily(targetMemberId, myMembership.getFamily().getId());
        }
    }

    private void deleteGuardianFromFamily(Long targetMemberId, Long familyId) {
        FamilyMembership membership = familyMembershipRepository
                .findByFamilyIdAndGuardianId(familyId, targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다."));
        familyMembershipRepository.delete(membership);
    }

    private void deleteSeniorFromFamily(Long targetMemberId, Long familyId) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다."));

        if (!seniorProfile.getFamily().getId().equals(familyId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어에 접근 권한이 없습니다.");
        }

        List<SeniorProfile> familySeniors = seniorProfileRepository.findAllByFamilyIdWithLock(familyId);
        if (familySeniors.size() <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "가족에 시니어가 최소 1명은 있어야 합니다.");
        }

        pointHistoryRepository.deleteBySeniorProfileId(seniorProfile.getId());
        seniorProfileRepository.deleteByIdDirectly(seniorProfile.getId());
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

    private SeniorProfile getSeniorProfileWithAccessCheck(Long memberId, Long guardianId) {
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(guardianId, seniorProfile.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "해당 시니어에 접근 권한이 없습니다.");
        }

        return seniorProfile;
    }
}
