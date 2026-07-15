package com.widyu.auth.application.guardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.auth.dto.RefreshTokenDto;
import com.widyu.auth.dto.request.RefreshTokenRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.LocalAccount;
import com.widyu.member.Member;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
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
@DisplayName("GuardianTokenService 단위 테스트")
class GuardianTokenServiceTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private MemberUtil memberUtil;

    @InjectMocks
    private GuardianTokenService guardianTokenService;

    @Test
    @DisplayName("유효한 리프레시 토큰으로 새로운 토큰 쌍을 반환한다")
    void 유효한_리프레시토큰으로_새로운_토큰쌍을_반환한다() {
        // given
        String refreshToken = "valid-refresh-token";
        Long memberId = 1L;
        RefreshTokenDto refreshTokenDto = new RefreshTokenDto(memberId, refreshToken, 604800L);
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", memberId);
        LocalAccount localAccount = LocalAccount.createLocalAccount(member, "test@test.com", "encoded");
        ReflectionTestUtils.setField(member, "localAccount", localAccount);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        TokenPairResponse expectedTokenPair = TokenPairResponse.of(memberId, "new-access", "new-refresh");

        given(jwtTokenProvider.retrieveRefreshToken(refreshToken)).willReturn(refreshTokenDto);
        given(memberUtil.getMemberByMemberId(memberId)).willReturn(member);
        given(jwtTokenProvider.generateTokenPair(any(), eq(MemberRole.USER), eq("local")))
                .willReturn(expectedTokenPair);

        // when
        TokenPairResponse result = guardianTokenService.reissueTokenPair(new RefreshTokenRequest(refreshToken));

        // then
        assertThat(result).isEqualTo(expectedTokenPair);
        verify(jwtTokenProvider, never()).createRefreshTokenDto(any());
        verify(jwtTokenProvider).generateTokenPair(memberId, MemberRole.USER, "local");
    }

    @Test
    @DisplayName("로컬 계정 보유 멤버는 loginType이 local로 재발급된다")
    void 로컬_계정_보유_멤버는_loginType이_local로_재발급된다() {
        // given
        Long memberId = 1L;
        String refreshToken = "refresh-token";
        RefreshTokenDto dto = new RefreshTokenDto(memberId, refreshToken, 604800L);
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", memberId);
        LocalAccount localAccount = LocalAccount.createLocalAccount(member, "test@test.com", "pw");
        ReflectionTestUtils.setField(member, "localAccount", localAccount);
        ReflectionTestUtils.setField(member, "socialAccounts", new ArrayList<>());
        TokenPairResponse tokenPair = TokenPairResponse.of(memberId, "access", "refresh");

        given(jwtTokenProvider.retrieveRefreshToken(refreshToken)).willReturn(dto);
        given(memberUtil.getMemberByMemberId(memberId)).willReturn(member);
        given(jwtTokenProvider.generateTokenPair(any(), any(), eq("local"))).willReturn(tokenPair);

        // when
        guardianTokenService.reissueTokenPair(new RefreshTokenRequest(refreshToken));

        // then
        verify(jwtTokenProvider, never()).createRefreshTokenDto(any());
        verify(jwtTokenProvider).generateTokenPair(any(), eq(MemberRole.USER), eq("local"));
    }

    @Test
    @DisplayName("소셜 계정만 있는 멤버는 loginType이 소셜 provider로 재발급된다")
    void 소셜_계정만_있는_멤버는_loginType이_소셜_provider로_재발급된다() {
        // given
        Long memberId = 1L;
        String refreshToken = "refresh-token";
        RefreshTokenDto dto = new RefreshTokenDto(memberId, refreshToken, 604800L);
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "localAccount", null);

        SocialAccount kakaoAccount = SocialAccount.createSocialAccount(
                "test@kakao.com", "kakao", "kakao-oauth-id", member
        );
        ReflectionTestUtils.setField(member, "socialAccounts", List.of(kakaoAccount));
        TokenPairResponse tokenPair = TokenPairResponse.of(memberId, "access", "refresh");

        given(jwtTokenProvider.retrieveRefreshToken(refreshToken)).willReturn(dto);
        given(memberUtil.getMemberByMemberId(memberId)).willReturn(member);
        given(jwtTokenProvider.generateTokenPair(any(), any(), eq("kakao"))).willReturn(tokenPair);

        // when
        guardianTokenService.reissueTokenPair(new RefreshTokenRequest(refreshToken));

        // then
        verify(jwtTokenProvider, never()).createRefreshTokenDto(any());
        verify(jwtTokenProvider).generateTokenPair(any(), eq(MemberRole.USER), eq("kakao"));
    }
}
