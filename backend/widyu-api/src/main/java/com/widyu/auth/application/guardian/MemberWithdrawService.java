package com.widyu.auth.application.guardian;

import com.widyu.auth.application.guardian.oauth.strategy.SocialLoginStrategy;
import com.widyu.auth.application.guardian.oauth.strategy.SocialLoginStrategyFactory;
import com.widyu.auth.dto.request.MemberWithdrawRequest;
import com.widyu.auth.OAuthProvider;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.member.Member;
import com.widyu.member.SocialAccount;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
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
public class MemberWithdrawService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SocialLoginStrategyFactory strategyFactory;
    private final MemberUtil memberUtil;

    @Transactional
    public void withdrawMember(MemberWithdrawRequest request) {
        Member member = memberUtil.getCurrentMember();
        
        log.info("회원 탈퇴 시작: memberId={}, reason={}", member.getId(), request.reason());

        // 1. 리프레시 토큰 삭제
        refreshTokenRepository.deleteById(member.getId());

        // 2. 연동된 모든 소셜 계정 탈퇴
        withdrawAllSocialAccounts(member);

        // 3. FamilyMembership 삭제
        familyMembershipRepository.deleteByGuardianId(member.getId());

        // 4. 개인정보 마스킹 (GDPR 준수)
        member.maskPersonalInfo();

        // 5. 로컬 계정 삭제
        member.withdraw();

        // 6. 회원 데이터 저장
        memberRepository.save(member);
        
        log.info("회원 탈퇴 완료: memberId={}", member.getId());
    }

    private void withdrawAllSocialAccounts(Member member) {
        for (SocialAccount socialAccount : member.getSocialAccounts()) {
            String provider = socialAccount.getProvider();
            
            // 카카오의 경우 어드민 키로 탈퇴하므로 액세스 토큰 불필요
            if ("kakao".equals(provider)) {
                try {
                    withdrawSocialAccount(provider, null, socialAccount.getOauthId());
                } catch (Exception e) {
                    log.warn("카카오 계정 탈퇴 실패하지만 진행 계속: oauthId={}, error={}", 
                            socialAccount.getOauthId(), e.getMessage());
                }
            } 
            // 애플, 네이버의 경우 저장된 리프레시 토큰 사용
            else if (("apple".equals(provider) || "naver".equals(provider)) 
                    && socialAccount.getRefreshToken() != null && !socialAccount.getRefreshToken().isBlank()) {
                try {
                    withdrawSocialAccount(provider, socialAccount.getRefreshToken(), socialAccount.getOauthId());
                } catch (Exception e) {
                    log.warn("{} 계정 탈퇴 실패하지만 진행 계속: oauthId={}, error={}", 
                            provider, socialAccount.getOauthId(), e.getMessage());
                }
            } else {
                log.warn("소셜 계정 탈퇴를 위한 토큰 없음: provider={}, oauthId={}", 
                        provider, socialAccount.getOauthId());
            }
        }
    }

    private void withdrawSocialAccount(String providerName, String accessToken, String oauthId) {
        try {
            OAuthProvider provider = OAuthProvider.from(providerName);
            SocialLoginStrategy strategy = strategyFactory.getStrategy(providerName);
            
            strategy.withdrawSocialAccount(accessToken, oauthId);
            
            log.info("소셜 계정 탈퇴 성공: provider={}, oauthId={}", providerName, oauthId);
        } catch (Exception e) {
            log.error("소셜 계정 탈퇴 실패: provider={}, oauthId={}, error={}", 
                    providerName, oauthId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}