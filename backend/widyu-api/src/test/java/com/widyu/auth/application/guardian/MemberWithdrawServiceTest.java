package com.widyu.auth.application.guardian;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.auth.application.guardian.oauth.strategy.SocialLoginStrategy;
import com.widyu.auth.application.guardian.oauth.strategy.SocialLoginStrategyFactory;
import com.widyu.auth.dto.request.MemberWithdrawRequest;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import com.widyu.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberWithdrawService 단위 테스트")
class MemberWithdrawServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SocialLoginStrategyFactory strategyFactory;
    @Mock private MemberUtil memberUtil;
    @Mock private SocialLoginStrategy kakaoStrategy;
    @Mock private SocialLoginStrategy appleStrategy;

    @InjectMocks
    private MemberWithdrawService memberWithdrawService;

    @Test
    @DisplayName("소셜 계정이 없는 회원 탈퇴 시 리프레시 토큰 삭제 및 회원 정보를 저장한다")
    void 소셜계정_없는_회원_탈퇴_시_토큰_삭제하고_회원_저장() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("서비스 불만족"));

        // then
        verify(refreshTokenRepository).deleteById(1L);
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("카카오 계정이 있는 회원 탈퇴 시 카카오 탈퇴 API를 호출한다")
    void 카카오_계정_보유_회원_탈퇴_시_카카오_탈퇴_API_호출() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount kakaoAccount = SocialAccount.createSocialAccount("k@k.com", "kakao", "kakao-id", member);
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(kakaoAccount));

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(strategyFactory.getStrategy("kakao")).willReturn(kakaoStrategy);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(kakaoStrategy).withdrawSocialAccount(null, "kakao-id");
    }

    @Test
    @DisplayName("애플 계정(리프레시 토큰 있음) 탈퇴 시 리프레시 토큰으로 탈퇴 API를 호출한다")
    void 애플_계정_보유_회원_탈퇴_시_리프레시토큰으로_탈퇴_API_호출() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount appleAccount = SocialAccount.createSocialAccount(
                "a@a.com", "apple", "apple-id", "apple-refresh-token", member
        );
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(appleAccount));

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(strategyFactory.getStrategy("apple")).willReturn(appleStrategy);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(appleStrategy).withdrawSocialAccount("apple-refresh-token", "apple-id");
    }

    @Test
    @DisplayName("카카오 탈퇴 실패 시에도 전체 탈퇴 흐름(토큰 삭제, 회원 저장)은 계속 진행된다")
    void 카카오_탈퇴_실패_시에도_전체_탈퇴_흐름이_계속된다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount kakaoAccount = SocialAccount.createSocialAccount("k@k.com", "kakao", "kakao-id", member);
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(kakaoAccount));

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(strategyFactory.getStrategy("kakao")).willReturn(kakaoStrategy);
        willThrow(new RuntimeException("카카오 서버 오류")).given(kakaoStrategy).withdrawSocialAccount(any(), any());

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(refreshTokenRepository).deleteById(1L);
        verify(memberRepository).save(member);
    }

    @Test
    @DisplayName("리프레시 토큰이 없는 애플 계정은 탈퇴 API를 호출하지 않는다")
    void 리프레시토큰_없는_애플_계정은_탈퇴_API_호출하지_않는다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        SocialAccount appleAccountNoToken = SocialAccount.createSocialAccount(
                "a@a.com", "apple", "apple-id", null, member
        );
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(appleAccountNoToken));

        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(appleStrategy, never()).withdrawSocialAccount(any(), any());
    }

    @Test
    @DisplayName("회원 탈퇴 시 개인정보가 마스킹된다")
    void 회원_탈퇴_시_개인정보가_마스킹된다() {
        // given
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        memberWithdrawService.withdrawMember(new MemberWithdrawRequest("탈퇴 사유"));

        // then
        verify(memberRepository).save(
                org.mockito.ArgumentMatchers.argThat(
                        m -> !"홍길동".equals(m.getName())
                )
        );
    }
}
