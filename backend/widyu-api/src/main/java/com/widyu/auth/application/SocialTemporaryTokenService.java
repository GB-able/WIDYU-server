package com.widyu.auth.application;

import com.widyu.auth.dto.SocialTemporaryTokenDto;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialTemporaryTokenService {

    private final JwtTokenProvider jwtTokenProvider;

    public String createSocialTemporaryToken(Long memberId, String provider, String oauthId, String email) {
        String token = jwtTokenProvider.generateSocialTemporaryToken(memberId, provider, oauthId, email);
        
        log.info("소셜 임시 토큰 생성: memberId={}, provider={}", memberId, provider);
        
        return token;
    }

    public SocialTemporaryTokenDto validateAndRetrieve(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_TEMPORARY_TOKEN);
        }

        SocialTemporaryTokenDto tokenDto = jwtTokenProvider.retrieveSocialTemporaryToken(token);

        log.info("소셜 임시 토큰 검증 성공: memberId={}, provider={}", 
                tokenDto.memberId(), tokenDto.provider());

        return tokenDto;
    }

    public void deleteSocialTemporaryToken(String token) {
        // JWT 토큰은 stateless이므로 삭제할 필요 없음
        log.info("소셜 임시 토큰 삭제 (JWT는 자동 만료)");
    }
}