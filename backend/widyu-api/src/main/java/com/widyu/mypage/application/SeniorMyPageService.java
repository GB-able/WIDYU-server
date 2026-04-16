package com.widyu.mypage.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.PointHistory;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.response.EmergencyContactResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.PointHistoryResponse;
import com.widyu.mypage.dto.response.SeniorInfoResponse;
import com.widyu.mypage.dto.response.SeniorProfileDetailResponse;
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
public class SeniorMyPageService {

    private final MemberUtil memberUtil;
    private final S3Service s3Service;
    private final PointHistoryRepository pointHistoryRepository;
    private final FamilyConnectionRepository familyConnectionRepository;

    public SeniorInfoResponse getSeniorInfo() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = member.getSeniorProfile();

        return SeniorInfoResponse.of(
                member.getId(),
                member.getProfileImage(),
                member.getName(),
                seniorProfile.getPoints()
        );
    }

    public FamilyCodeResponse getFamilyCode() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = member.getSeniorProfile();
        return FamilyCodeResponse.of(seniorProfile.getFamilyCode());
    }

    public SeniorProfileDetailResponse getProfileDetail() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = member.getSeniorProfile();
        return SeniorProfileDetailResponse.of(member, seniorProfile);
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
    public void updatePhoneNumber(UpdatePhoneRequest request) {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        member.updatePhoneNumber(request.phoneNumber());
    }

    public PointHistoryResponse getPointHistory() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = member.getSeniorProfile();

        List<PointHistory> histories = pointHistoryRepository
                .findAllBySeniorProfileIdOrderByCreatedAtDesc(seniorProfile.getId());

        return PointHistoryResponse.of(seniorProfile.getPoints(), histories);
    }

    public EmergencyContactResponse getEmergencyContacts() {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = member.getSeniorProfile();

        List<FamilyConnection> connections = familyConnectionRepository
                .findAllBySeniorIdWithGuardian(seniorProfile.getId());

        return EmergencyContactResponse.of(connections);
    }

    @Transactional
    public void updateRepresentativeContact(Long guardianId) {
        Member member = MyPageProfileService.getCurrentMember(memberUtil);
        SeniorProfile seniorProfile = member.getSeniorProfile();

        List<FamilyConnection> connections = familyConnectionRepository
                .findAllBySeniorIdWithGuardian(seniorProfile.getId());

        boolean guardianFound = connections.stream()
                .anyMatch(c -> c.getGuardian().getId().equals(guardianId));
        if (!guardianFound) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "가족 구성원을 찾을 수 없습니다.");
        }

        connections.forEach(c -> c.setRepresentative(c.getGuardian().getId().equals(guardianId)));
    }
}
