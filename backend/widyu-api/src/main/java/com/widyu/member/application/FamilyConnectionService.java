package com.widyu.member.application;

import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.dto.request.FamilyJoinRequest;
import com.widyu.member.dto.response.FamilyJoinResponse;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FamilyConnectionService {

    private final MemberUtil memberUtil;
    private final SeniorProfileRepository seniorProfileRepository;
    private final FamilyConnectionRepository familyConnectionRepository;

    @Transactional
    public FamilyJoinResponse joinFamily(FamilyJoinRequest request) {
        Member currentMember = memberUtil.getCurrentMember();

        if (currentMember.getType() != MemberType.GUARDIAN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "보호자 회원만 초대코드로 가족에 참여할 수 있습니다.");
        }

        SeniorProfile seniorProfile = seniorProfileRepository.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND, request.inviteCode()));

        if (familyConnectionRepository.existsBySeniorIdAndGuardianId(seniorProfile.getId(), currentMember.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_CONNECTED_TO_FAMILY, "이미 해당 가족에 연결되어 있습니다.");
        }

        FamilyConnection connection = FamilyConnection.createConnection(seniorProfile, currentMember);
        familyConnectionRepository.save(connection);

        log.info("가족 연결 완료: guardianId={}, seniorId={}", currentMember.getId(), seniorProfile.getId());
        return FamilyJoinResponse.from(seniorProfile);
    }
}
