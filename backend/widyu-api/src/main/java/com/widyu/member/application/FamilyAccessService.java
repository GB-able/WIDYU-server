package com.widyu.member.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.FamilyMembership;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FamilyAccessService {

    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;

    public void verifyFamilyAccess(Long guardianId, Long targetMemberId) {
        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));

        if (targetMember.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어의 리소스만 접근할 수 있습니다.");
        }

        if (targetMember.getSeniorProfile() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "시니어 프로필이 없습니다.");
        }

        boolean isFamily = familyMembershipRepository.existsByGuardianIdAndSeniorProfileId(
                guardianId,
                targetMember.getSeniorProfile().getId()
        );

        if (!isFamily) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "가족으로 연결된 시니어만 접근할 수 있습니다.");
        }
    }

    public void verifyLeaderAccess(Long guardianId, Long targetMemberId) {
        verifyFamilyAccess(guardianId, targetMemberId);

        Member targetMember = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 사용자입니다."));

        boolean isLeader = familyMembershipRepository.existsByGuardianIdAndSeniorProfileIdAndIsLeaderTrue(
                guardianId,
                targetMember.getSeniorProfile().getId()
        );
        if (!isLeader) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 수정할 수 있습니다.");
        }
    }

    public void verifyGuardianLeader(Long guardianId) {
        boolean isLeader = familyMembershipRepository.findByGuardianId(guardianId)
                .map(FamilyMembership::isLeader)
                .orElse(false);
        if (!isLeader) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "방장만 수행할 수 있습니다.");
        }
    }
}
