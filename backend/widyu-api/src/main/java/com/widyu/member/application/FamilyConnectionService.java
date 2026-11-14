package com.widyu.member.application;

import com.widyu.auth.infrastructure.AuthLimitStore;
import com.widyu.auth.infrastructure.ClientIpResolver;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.dto.response.FamilyJoinResponse;
import com.widyu.member.dto.request.FamilyJoinRequest;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyConnectionService {

    private final MemberUtil memberUtil;
    private final FamilyRepository familyRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final AuthLimitStore authLimitStore;
    private final ClientIpResolver clientIpResolver;

    @Transactional
    public FamilyJoinResponse joinFamily(FamilyJoinRequest request) {
        Member currentMember = memberUtil.getCurrentMember();

        if (currentMember.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "보호자 회원만 초대코드로 가족에 참여할 수 있습니다.");
        }

        // 맞는 코드면 승인 없이 가족이 된다. 회원과 IP별 실패를 세어 코드 대입을 막는다 (LLD-0147)
        AuthLimitStore.LoginAttempt attempt = authLimitStore.reserveFamilyJoin(
                currentMember.getId(), clientIpResolver.resolve());
        FamilyJoinResponse response;
        try {
            response = join(currentMember, request);
        } catch (BusinessException exception) {
            authLimitStore.completeLogin(attempt, false);
            throw exception;
        }
        authLimitStore.completeLogin(attempt, true);
        return response;
    }

    private FamilyJoinResponse join(Member currentMember, FamilyJoinRequest request) {
        Family family = familyRepository.findByFamilyCode(request.familyCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND, request.familyCode()));

        if (familyMembershipRepository.findByGuardianId(currentMember.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_CONNECTED_TO_FAMILY, "이미 가족에 소속되어 있습니다.");
        }

        boolean hasLeader = familyMembershipRepository.existsByFamilyIdAndIsLeaderTrue(family.getId());
        int sortOrder = familyMembershipRepository.findMaxSortOrderByFamilyId(family.getId()) + 1;
        FamilyMembership membership;
        if (hasLeader) {
            membership = FamilyMembership.createMembership(family, currentMember, sortOrder);
        } else {
            membership = FamilyMembership.createLeaderMembership(family, currentMember);
            membership.updateSortOrder(sortOrder);
        }
        familyMembershipRepository.save(membership);
        familyRepository.incrementOrderRevision(family.getId());

        List<SeniorProfile> seniors = seniorProfileRepository.findAllByFamilyIdWithMember(family.getId());

        log.info("가족 연결 완료: guardianId={}, familyId={}", currentMember.getId(), family.getId());
        return FamilyJoinResponse.from(family, seniors);
    }
}
