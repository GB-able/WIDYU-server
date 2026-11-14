package com.widyu.auth.application.guardian.oauth;

import com.widyu.auth.OAuthProvider;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.member.Member;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이메일로 가입한 보호자가 나중에 애플로 로그인하면, 애플은 전화번호를 주지 않아 서버가 기존 회원을 찾지 못하고 빈 새 회원을 만든다.
 * 번호 등록 단계에서 SMS로 인증한 번호의 주인이 기존 보호자로 드러나면 애플 계정을 그 보호자에게 옮기고 빈 회원은 탈퇴 처리한다.
 * SMS 인증은 비밀번호 재설정과 같은 수준의 계정 주인 확인이다 (LLD-0157)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppleAccountMergeService {

    private static final String APPLE = OAuthProvider.APPLE.getValue();

    private final MemberRepository memberRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // 호출자 트랜잭션 안에서 돈다. 번호 주인 확인은 호출자가 SMS 임시 토큰으로 마쳤다
    public TokenPairResponse merge(Member appleMember, Member phoneOwner) {
        validateEmptyAppleMember(appleMember);

        // 같은 보호자에게 두 애플 계정이 동시에 합쳐지지 않게 보호자 행을 잠근 뒤 확인한다
        Member owner = memberRepository.findByIdForUpdate(phoneOwner.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        validatePhoneOwner(owner);

        if (memberRepository.moveSocialAccount(appleMember, owner, APPLE) != 1) {
            log.warn("애플 계정 이전 대상 없음(동시 요청): appleMemberId={}", appleMember.getId());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 이전 UPDATE가 영속성 컨텍스트를 비웠으므로 빈 회원을 다시 읽어 탈퇴 처리한다. 애플 연동 해제는 하지 않는다(계정이 옮겨갔다)
        Member emptied = memberRepository.findById(appleMember.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        emptied.maskPersonalInfo();
        emptied.markWithdrawn(LocalDateTime.now());
        refreshTokenRepository.deleteById(emptied.getId());

        log.info("애플 계정을 기존 보호자에 합침: fromMemberId={}, toMemberId={}", emptied.getId(), owner.getId());
        return jwtTokenProvider.generateTokenPair(owner.getId(), MemberRole.USER, APPLE);
    }

    // 방금 애플 로그인으로 만들어져 애플 계정 말고는 아무것도 없는 회원만 합친다. 무엇이 쌓인 회원을 지우면 자료가 사라진다
    private void validateEmptyAppleMember(Member member) {
        boolean onlyApple = member.getSocialAccounts().size() == 1
                && APPLE.equals(member.getSocialAccounts().get(0).getProvider());
        if (member.getType() != MemberType.GUARDIAN
                || member.getLocalAccount() != null
                || !onlyApple
                || familyMembershipRepository.findByGuardianId(member.getId()).isPresent()) {
            log.warn("빈 회원이 아니라 애플 계정을 합치지 않음: memberId={}", member.getId());
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
        }
    }

    private void validatePhoneOwner(Member owner) {
        if (owner.getType() != MemberType.GUARDIAN
                || owner.getStatus() != Status.ACTIVE
                || owner.getWithdrawnAt() != null) {
            log.warn("번호 주인이 활성 보호자가 아니라 애플 계정을 합치지 않음: ownerId={}", owner.getId());
            throw new BusinessException(ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
        }
        boolean ownerHasApple = owner.getSocialAccounts().stream()
                .anyMatch(account -> APPLE.equals(account.getProvider()));
        if (ownerHasApple) {
            log.warn("이미 애플 계정이 있는 보호자라 합치지 않음: ownerId={}", owner.getId());
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        }
    }
}
