package com.widyu.global.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.auth.dto.AccessTokenDto;
import com.widyu.auth.dto.SocialTemporaryTokenDto;
import com.widyu.auth.dto.TemporaryTokenDto;
import com.widyu.global.properties.JwtProperties;
import com.widyu.member.MemberRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String ACCESS_SECRET = "test-access-token-secret-key-for-testing";
    private static final String REFRESH_SECRET = "test-refresh-token-secret-key-for-testing";
    private static final String TEMPORARY_SECRET = "test-temporary-token-secret-key-for-testing";

    // ======================== 토큰 종류 검증 ========================

    @Test
    @DisplayName("비밀값이 모두 같을 때 소셜 임시 토큰을 액세스 토큰으로 파싱하면 거부한다")
    void 비밀값이_같아도_소셜_임시_토큰은_액세스_토큰으로_통과하지_않는다() {
        // given
        JwtUtil jwtUtil = 비밀값이_모두_같은_JwtUtil();
        String socialTemporaryToken = jwtUtil.generateSocialTemporaryToken(1L, "KAKAO", "oauth-id", "a@b.c");

        // when
        AccessTokenDto result = jwtUtil.parseAccessToken(socialTemporaryToken);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("비밀값이 모두 같을 때 리프레시 토큰을 액세스 토큰으로 파싱하면 거부한다")
    void 비밀값이_같아도_리프레시_토큰은_액세스_토큰으로_통과하지_않는다() {
        // given
        JwtUtil jwtUtil = 비밀값이_모두_같은_JwtUtil();
        String refreshToken = jwtUtil.generateRefreshTokenDto(1L).tokenValue();

        // when
        AccessTokenDto result = jwtUtil.parseAccessToken(refreshToken);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("비밀값이 모두 같을 때 액세스 토큰을 리프레시, 임시 토큰으로 파싱하면 거부한다")
    void 비밀값이_같아도_액세스_토큰은_다른_파서를_통과하지_않는다() {
        // given
        JwtUtil jwtUtil = 비밀값이_모두_같은_JwtUtil();
        String accessToken = jwtUtil.generateAccessToken(1L, MemberRole.USER, "LOCAL");

        // when & then
        assertThat(jwtUtil.parseRefreshToken(accessToken)).isNull();
        assertThat(jwtUtil.parseTemporaryToken(accessToken)).isNull();
        assertThat(jwtUtil.parseSocialTemporaryToken(accessToken)).isNull();
    }

    @Test
    @DisplayName("종류 헤더가 없는 토큰을 액세스 토큰으로 파싱하면 거부한다")
    void 종류_헤더가_없는_토큰은_거부한다() {
        // given
        JwtUtil jwtUtil = 비밀값이_모두_같은_JwtUtil();
        String token = Jwts.builder()
                .setSubject("1")
                .claim("role", MemberRole.USER.name())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes()))
                .compact();

        // when
        AccessTokenDto result = jwtUtil.parseAccessToken(token);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("발급한 액세스 토큰을 액세스 토큰으로 파싱하면 회원과 역할을 반환한다")
    void 액세스_토큰은_액세스_파서를_통과한다() {
        // given
        JwtUtil jwtUtil = new JwtUtil(서로_다른_비밀값());
        String accessToken = jwtUtil.generateAccessToken(7L, MemberRole.USER, "LOCAL");

        // when
        AccessTokenDto result = jwtUtil.parseAccessToken(accessToken);

        // then
        assertThat(result.memberId()).isEqualTo(7L);
        assertThat(result.memberRole()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("소셜 임시 토큰을 임시 토큰으로 파싱하면 USER 역할이 그대로 남아 SMS 임시 토큰과 구분된다")
    void 소셜_임시_토큰과_SMS_임시_토큰은_역할로_구분된다() {
        // given
        JwtUtil jwtUtil = new JwtUtil(서로_다른_비밀값());
        String socialTemporaryToken = jwtUtil.generateSocialTemporaryToken(7L, "KAKAO", "oauth-id", "a@b.c");
        String smsTemporaryToken = jwtUtil.generateTemporaryToken("temp-member-id");

        // when
        SocialTemporaryTokenDto social = jwtUtil.parseSocialTemporaryToken(socialTemporaryToken);
        TemporaryTokenDto socialAsTemporary = jwtUtil.parseTemporaryToken(socialTemporaryToken);
        TemporaryTokenDto sms = jwtUtil.parseTemporaryToken(smsTemporaryToken);

        // then
        assertThat(social.memberId()).isEqualTo(7L);
        assertThat(socialAsTemporary.memberRole()).isEqualTo(MemberRole.USER);
        assertThat(sms.memberRole()).isEqualTo(MemberRole.TEMPORARY);
        assertThat(sms.temporaryMemberId()).isEqualTo("temp-member-id");
    }

    // ======================== 기동 시 비밀값 검사 ========================

    @Test
    @DisplayName("비밀값 두 개가 같으면 설정을 만들 수 없어 기동하지 않는다")
    void 비밀값이_같으면_설정_생성에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new JwtProperties(ACCESS_SECRET, 3600L, REFRESH_SECRET, 3600L, ACCESS_SECRET, 300_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("서로 달라야");
    }

    @Test
    @DisplayName("비밀값이 32바이트보다 짧으면 설정을 만들 수 없어 기동하지 않는다")
    void 비밀값이_짧으면_설정_생성에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new JwtProperties(ACCESS_SECRET, 3600L, "short-secret", 3600L, TEMPORARY_SECRET, 300_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.refresh-token-secret");
    }

    @Test
    @DisplayName("비밀값이 없으면 설정을 만들 수 없어 기동하지 않는다")
    void 비밀값이_없으면_설정_생성에_실패한다() {
        // when & then
        assertThatThrownBy(() -> new JwtProperties(null, 3600L, REFRESH_SECRET, 3600L, TEMPORARY_SECRET, 300_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.access-token-secret");
    }

    private static JwtProperties 서로_다른_비밀값() {
        return new JwtProperties(ACCESS_SECRET, 3600L, REFRESH_SECRET, 3600L, TEMPORARY_SECRET, 300_000L);
    }

    // 기동 검사가 막는 설정이지만, 종류 검증이 비밀값과 관계없이 동작하는지 보려고 설정 객체를 대역으로 만든다
    private static JwtUtil 비밀값이_모두_같은_JwtUtil() {
        JwtProperties properties = mock(JwtProperties.class);
        given(properties.accessTokenSecret()).willReturn(ACCESS_SECRET);
        given(properties.refreshTokenSecret()).willReturn(ACCESS_SECRET);
        given(properties.temporaryTokenSecret()).willReturn(ACCESS_SECRET);
        given(properties.accessTokenExpirationMilliTime()).willReturn(3_600_000L);
        given(properties.refreshTokenExpirationMilliTime()).willReturn(3_600_000L);
        given(properties.refreshTokenExpirationTime()).willReturn(3600L);
        given(properties.temporaryTokenExpirationTime()).willReturn(300_000L);
        return new JwtUtil(properties);
    }
}
