package com.widyu.global.security;

import static com.widyu.global.constant.SecurityConstant.TOKEN_ROLE_NAME;

import com.widyu.auth.RefreshToken;
import com.widyu.auth.TemporaryMember;
import com.widyu.auth.dto.AccessTokenDto;
import com.widyu.auth.dto.RefreshTokenDto;
import com.widyu.auth.dto.SocialTemporaryTokenDto;
import com.widyu.auth.dto.TemporaryTokenDto;
import com.widyu.auth.dto.response.TemporaryTokenResponse;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.JwtUtil;
import com.widyu.member.MemberRole;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenPairResponse generateTokenPair(Long memberId, MemberRole memberRole, String loginType) {
        String accessToken = generateAccessToken(memberId, memberRole, loginType);
        String refreshToken = generateAndSaveRefreshToken(memberId);

        return TokenPairResponse.of(memberId, accessToken, refreshToken);
    }

    public TemporaryTokenResponse generateTemporaryToken(TemporaryMember temporaryMember) {
        String temporaryToken = generateTemporaryTokenValue(temporaryMember);
        return TemporaryTokenResponse.from(temporaryToken);
    }

    public String generateSocialTemporaryToken(Long memberId, String provider, String oauthId, String email) {
        return jwtUtil.generateSocialTemporaryToken(memberId, provider, oauthId, email);
    }

    public String generateAccessToken(Long memberId, MemberRole memberRole, String loginType) {
        return jwtUtil.generateAccessToken(memberId, memberRole, loginType);
    }

    public AccessTokenDto generateAccessTokenDto(Long memberId, MemberRole memberRole, String loginType) {
        return jwtUtil.generateAccessTokenDto(memberId, memberRole, loginType);
    }

    public AccessTokenDto retrieveAccessToken(String accessTokenValue) {
        try {
            return jwtUtil.parseAccessToken(accessTokenValue);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.EXPIRED_ACCESS_TOKEN);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_ACCESS_TOKEN);
        }
    }

    public RefreshTokenDto retrieveRefreshToken(String refreshTokenValue) {
        RefreshTokenDto refreshTokenDto = parseRefreshTokenSafely(refreshTokenValue);
        validateRefreshTokenExists(refreshTokenDto.memberId());
        return refreshTokenDto;
    }

    public TemporaryTokenDto retrieveTemporaryToken(String temporaryTokenValue) {
        try {
            return jwtUtil.parseTemporaryToken(temporaryTokenValue);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TEMPORARY_TOKEN_EXPIRED);
        } catch (Exception e) {
            log.debug("Temporary Token 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    public SocialTemporaryTokenDto retrieveSocialTemporaryToken(String socialTemporaryTokenValue) {
        try {
            return jwtUtil.parseSocialTemporaryToken(socialTemporaryTokenValue);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TEMPORARY_TOKEN_EXPIRED);
        } catch (Exception e) {
            log.debug("Social Temporary Token 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_TEMPORARY_TOKEN);
        }
    }

    public AccessTokenDto reissueAccessTokenIfExpired(String accessTokenValue) {
        try {
            jwtUtil.parseAccessToken(accessTokenValue);
            return null; // 토큰이 유효하면 재발급 불필요
        } catch (ExpiredJwtException e) {
            return reissueAccessTokenFromExpired(e);
        }
    }

    public RefreshTokenDto createRefreshTokenDto(Long memberId) {
        RefreshTokenDto refreshTokenDto = jwtUtil.generateRefreshTokenDto(memberId);
        saveRefreshTokenToStorage(memberId, refreshTokenDto.tokenValue());
        return refreshTokenDto;
    }

    private String generateAndSaveRefreshToken(Long memberId) {
        String refreshTokenValue = jwtUtil.generateRefreshToken(memberId);
        saveRefreshTokenToStorage(memberId, refreshTokenValue);
        return refreshTokenValue;
    }

    private void saveRefreshTokenToStorage(Long memberId, String refreshTokenValue) {
        RefreshToken refreshToken = RefreshToken.builder()
                .memberId(memberId)
                .token(refreshTokenValue)
                .ttl(jwtUtil.getRefreshTokenExpirationTime())
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    private String generateTemporaryTokenValue(TemporaryMember temporaryMember) {
        return jwtUtil.generateTemporaryToken(temporaryMember.getId());
    }

    private RefreshTokenDto parseRefreshTokenSafely(String refreshTokenValue) {
        try {
            RefreshTokenDto refreshTokenDto = jwtUtil.parseRefreshToken(refreshTokenValue);
            if (refreshTokenDto == null) {
                throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            return refreshTokenDto;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateRefreshTokenExists(Long memberId) {
        if (refreshTokenRepository.findById(memberId).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private AccessTokenDto reissueAccessTokenFromExpired(ExpiredJwtException expiredException) {
        Long memberId = Long.parseLong(expiredException.getClaims().getSubject());
        MemberRole memberRole = MemberRole.valueOf(
                expiredException.getClaims().get(TOKEN_ROLE_NAME, String.class)
        );
        String loginType = expiredException.getClaims().get("loginType", String.class);

        return generateAccessTokenDto(memberId, memberRole, loginType);
    }
}
