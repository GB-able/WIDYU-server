package com.widyu.admin.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.retry.RetryOnPointConflict;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.PointHistory;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPointGrantService {

    private final MemberRepository memberRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final PointHistoryRepository pointHistoryRepository;

    @RetryOnPointConflict
    @Transactional
    public long grant(Long memberId, long amount) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getType() != MemberType.SENIOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "포인트는 시니어 회원에게만 지급할 수 있습니다.");
        }

        SeniorProfile profile = seniorProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        profile.addPoints(amount);
        pointHistoryRepository.save(PointHistory.earn(profile, amount, "[관리자 테스트 지급]"));

        return profile.getPoints();
    }
}
