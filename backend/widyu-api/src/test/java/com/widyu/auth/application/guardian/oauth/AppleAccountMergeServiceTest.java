package com.widyu.auth.application.guardian.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.member.Member;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import com.widyu.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("애플 계정 합치기 단위 테스트")
class AppleAccountMergeServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private EntityManager entityManager;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AppleAccountMergeService appleAccountMergeService;

    @Test
    @DisplayName("빈 애플 회원을 활성 보호자에 합치면 애플 계정을 옮기고 빈 회원은 탈퇴 처리한 뒤 보호자 토큰을 돌려준다")
    void 빈_애플_회원을_활성_보호자에_합치면_보호자_토큰을_돌려준다() {
        // given
        Member appleMember = appleMember(38L);
        Member owner = guardian(9L);
        Member emptied = Member.createMember(MemberType.GUARDIAN, "애플유저", null);
        ReflectionTestUtils.setField(emptied, "id", 38L);
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(0);
        given(memberRepository.moveSocialAccount(appleMember, owner, "APPLE")).willReturn(1);
        given(memberRepository.findById(38L)).willReturn(Optional.of(emptied));
        given(jwtTokenProvider.generateTokenPair(9L, MemberRole.USER, "APPLE"))
                .willReturn(TokenPairResponse.of(9L, "owner-access", "owner-refresh"));

        // when
        TokenPairResponse tokenPair = appleAccountMergeService.merge(appleMember, owner, "01000000002");

        // then
        assertThat(tokenPair.memberId()).isEqualTo(9L);
        assertThat(emptied.getStatus()).isEqualTo(Status.INACTIVE);
        assertThat(emptied.getWithdrawnAt()).isNotNull();
        assertThat(emptied.getName()).isEqualTo("탈퇴회원");
        verify(refreshTokenRepository).deleteById(38L);
    }

    @Test
    @DisplayName("번호 주인이 시니어면 합치지 않고 ALREADY_REGISTERED_PHONE_NUMBER 예외가 발생한다")
    void 번호_주인이_시니어면_합치지_않는다() {
        // given
        Member appleMember = appleMember(38L);
        Member senior = Member.createMember(MemberType.SENIOR, "어르신", "01000000002");
        ReflectionTestUtils.setField(senior, "id", 5L);
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, senior, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
        verify(memberRepository, never()).moveSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴한 보호자의 번호면 합치지 않고 ALREADY_REGISTERED_PHONE_NUMBER 예외가 발생한다")
    void 탈퇴한_보호자에게는_합치지_않는다() {
        // given
        Member appleMember = appleMember(38L);
        Member withdrawn = guardian(9L);
        withdrawn.markWithdrawn(java.time.LocalDateTime.now());
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, withdrawn, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
        verify(memberRepository, never()).moveSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("번호 주인 보호자에게 이미 애플 계정이 있으면 합치지 않고 SOCIAL_PROVIDER_ALREADY_LINKED 예외가 발생한다")
    void 이미_애플_계정이_있는_보호자에게는_합치지_않는다() {
        // given
        Member appleMember = appleMember(38L);
        Member owner = guardian(9L);
        owner.getSocialAccounts().add(
                SocialAccount.createSocialAccount("old@apple.com", "APPLE", "other-apple-id", owner));
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(0);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, owner, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        verify(memberRepository, never()).moveSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("애플 회원에 직접 만든 자료(가족 소속, 즐겨찾기 등)가 있으면 합치지 않고 ALREADY_REGISTERED_PHONE_NUMBER 예외가 발생한다")
    void 자료가_있는_애플_회원은_합치지_않는다() {
        // given
        Member appleMember = appleMember(38L);
        Member owner = guardian(9L);
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(1);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, owner, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
        verify(entityManager, never()).refresh(any(), any(LockModeType.class));
        verify(memberRepository, never()).moveSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("애플 회원에 다른 소셜 계정도 있으면 합치지 않고 ALREADY_REGISTERED_PHONE_NUMBER 예외가 발생한다")
    void 다른_소셜_계정도_있는_애플_회원은_합치지_않는다() {
        // given
        Member appleMember = appleMember(38L);
        appleMember.getSocialAccounts().add(
                SocialAccount.createSocialAccount("kakao@kakao.com", "KAKAO", "kakao-id", appleMember));
        Member owner = guardian(9L);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, owner, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
        verify(memberRepository, never()).moveSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("옮길 애플 계정이 없으면(동시 요청이 먼저 옮김) FORBIDDEN 예외가 발생하고 빈 회원을 탈퇴 처리하지 않는다")
    void 옮길_애플_계정이_없으면_예외가_발생한다() {
        // given
        Member appleMember = appleMember(38L);
        Member owner = guardian(9L);
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(0);
        given(memberRepository.moveSocialAccount(appleMember, owner, "APPLE")).willReturn(0);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, owner, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verify(memberRepository, never()).findById(any());
        verify(refreshTokenRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("보호자 행을 잠그고 다시 읽었을 때 번호가 인증한 번호와 다르면 합치지 않고 FORBIDDEN 예외가 발생한다")
    void 잠근_뒤_번호_주인이_바뀌었으면_합치지_않는다() {
        // given
        Member appleMember = appleMember(38L);
        Member owner = guardian(9L);
        given(memberRepository.countOwnedContentFlag(38L)).willReturn(0);
        willAnswer(invocation -> {
            ReflectionTestUtils.setField(owner, "phoneNumber", "01077777777");
            return null;
        }).given(entityManager).refresh(owner, LockModeType.PESSIMISTIC_WRITE);

        // when & then
        assertThatThrownBy(() -> appleAccountMergeService.merge(appleMember, owner, "01000000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verify(memberRepository, never()).moveSocialAccount(any(), any(), any());
    }

    private static Member appleMember(Long id) {
        Member member = Member.createMember(MemberType.GUARDIAN, "애플유저", null);
        ReflectionTestUtils.setField(member, "id", id);
        member.getSocialAccounts().add(
                SocialAccount.createSocialAccount("apple@apple.com", "APPLE", "apple-oauth-id", member));
        return member;
    }

    private static Member guardian(Long id) {
        Member member = Member.createMember(MemberType.GUARDIAN, "기존회원", "01000000002");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
