package com.widyu.domain.auth.application.guardian.oauth;

import com.widyu.domain.auth.application.SocialTemporaryTokenService;
import com.widyu.domain.auth.application.guardian.oauth.strategy.SocialLoginStrategy;
import com.widyu.domain.auth.application.guardian.oauth.strategy.SocialLoginStrategyFactory;
import com.widyu.domain.auth.application.guardian.oauth.strategy.UserInfo;
import com.widyu.domain.auth.dto.request.AppleSignUpRequest;
import com.widyu.domain.auth.dto.request.SocialLoginRequest;
import com.widyu.domain.auth.dto.response.SocialClientResponse;
import com.widyu.domain.auth.dto.response.SocialLoginResponse;
import com.widyu.domain.auth.dto.response.TokenPairResponse;
import com.widyu.domain.auth.dto.response.UserProfile;
import com.widyu.domain.auth.entity.OAuthProvider;
import com.widyu.domain.auth.dto.SocialTemporaryTokenDto;
import com.widyu.domain.auth.entity.TemporaryMember;
import com.widyu.domain.member.entity.Member;
import com.widyu.domain.member.entity.MemberRole;
import com.widyu.domain.member.entity.MemberType;
import com.widyu.domain.member.entity.SocialAccount;
import com.widyu.domain.member.repository.MemberRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.JwtUtil;
import com.widyu.global.util.TemporaryMemberUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {
    public static final String PROVIDER_LOCAL = "local";

    private final SocialLoginStrategyFactory strategyFactory;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TemporaryMemberUtil temporaryMemberUtil;
    private final SocialTemporaryTokenService socialTemporaryTokenService;

    public SocialLoginResponse socialLogin(String providerName, SocialLoginRequest request) {
        log.info("소셜 로그인 시도: provider={}", providerName);

        SocialLoginStrategy strategy = strategyFactory.getStrategy(providerName);
        OAuthProvider provider = strategy.getSupportedProvider();

        // 1. 요청 검증
        strategy.validateLoginRequest(request);

        // 2. 소셜 제공자에서 사용자 정보 획득
        SocialClientResponse originalResponse = strategy.getUserInfo(request);

        // 2.5. 프론트에서 전달받은 리프레시 토큰 설정 (네이버 등에서 사용)
        final SocialClientResponse socialResponse = strategy.enrichWithRefreshToken(originalResponse, request);

        // 3. 사용자 정보 후처리
        UserInfo userInfo = strategy.processUserInfo(socialResponse, request);

        // 4. 사용자 정보 검증
        strategy.validateUserInfo(userInfo);

        // 5. 기존 회원 확인
        Optional<Member> existingMember = findMemberByProvider(provider, socialResponse.oauthId());

        return existingMember
                .map(member -> handleExistingMemberLogin(member, provider, socialResponse.oauthId()))
                .orElseGet(() -> handleNewSocialAccount(provider, socialResponse, userInfo, socialResponse.oauthId()));
    }

    public void updatePhoneNumberIfAppleSignUp(AppleSignUpRequest request, HttpServletRequest httpServletRequest) {
        Member member = findAppleMemberByEmail(request.email());
        TemporaryMember temporaryMember = temporaryMemberUtil.getTemporaryMemberFromRequest(httpServletRequest);

        member.updatePhoneNumber(temporaryMember.getPhoneNumber());
        log.info("애플 사용자 전화번호 업데이트 완료: memberId={}, email={}", member.getId(), request.email());
    }

    public TokenPairResponse integrateSocialAccount(HttpServletRequest httpServletRequest) {
        // 헤더에서 소셜 임시 토큰 추출
        String socialTemporaryToken = JwtUtil.extractTokenFromAuthorizationHeader(httpServletRequest);
        if (socialTemporaryToken == null || socialTemporaryToken.trim().isEmpty()) {
            log.warn("소셜 임시 토큰 헤더가 누락됨");
            throw new BusinessException(ErrorCode.MISSING_SOCIAL_TEMPORARY_TOKEN);
        }

        // 임시 토큰으로 소셜 계정 정보 검증 및 조회
        SocialTemporaryTokenDto socialToken;
        try {
            socialToken = socialTemporaryTokenService.validateAndRetrieve(socialTemporaryToken);
        } catch (BusinessException e) {
            log.warn("소셜 임시 토큰 검증 실패: token={}, error={}", socialTemporaryToken.substring(0, Math.min(20, socialTemporaryToken.length())), e.getMessage());
            throw e; // 기존 에러 코드(INVALID_TEMPORARY_TOKEN, TEMPORARY_TOKEN_EXPIRED) 그대로 전파
        }

        // 기존 회원 조회
        Member member = memberRepository.findById(socialToken.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 요청한 제공자 검증
        OAuthProvider provider = OAuthProvider.from(socialToken.provider());

        // 현재 사용자가 이미 해당 제공자 계정을 가지고 있는지 확인
        boolean alreadyLinked = member.getSocialAccounts().stream()
                .anyMatch(account -> account.getProvider().equals(provider.getValue()));

        if (alreadyLinked) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        }

        // 다른 회원이 이미 해당 소셜 계정을 사용하고 있는지 확인
        Optional<Member> conflictMember = memberRepository.findBySocialAccounts_EmailAndSocialAccounts_Provider(
                socialToken.email(), provider.getValue());

        if (conflictMember.isPresent() && !conflictMember.get().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }

        // 새로운 소셜 계정을 현재 사용자에게 연동
        addSocialAccountToMember(member, provider, socialToken.oauthId(), socialToken.email());

        // 사용한 임시 토큰 삭제 (JWT는 자동 만료)
        socialTemporaryTokenService.deleteSocialTemporaryToken(socialTemporaryToken);

        log.info("소셜 계정 연동 완료: memberId={}, provider={}, email={}",
                member.getId(), provider.getValue(), socialToken.email());

        // 토큰 생성 후 반환 (새로 추가된 소셜 계정 정보로 loginType 설정)
        return jwtTokenProvider.generateTokenPair(member.getId(), MemberRole.USER, provider.getValue());
    }

    private SocialLoginResponse handleExistingMemberLogin(Member member, OAuthProvider provider, String oauthId) {
        log.info("기존 회원 로그인: providerId={}, oauthId={}, memberId={}",
                provider.getValue(), oauthId, member.getId());

        member.markSocialAsNotFirst(provider.getValue(), oauthId);
        boolean isFirstLogin = member.getSocialAccount(provider.getValue()).isFirst();

        return createSuccessfulLoginResponse(member, isFirstLogin, provider.getValue());
    }

    private SocialLoginResponse handleNewSocialAccount(OAuthProvider provider,
                                                       SocialClientResponse socialResponse,
                                                       UserInfo userInfo,
                                                       String oauthId) {
        Optional<Member> existingMember = findExistingMemberByUserInfo(userInfo);

        if (existingMember.isPresent() && hasOtherAccounts(existingMember.get())) {
            return handleConflictingSocialAccount(existingMember.get(), provider, userInfo, oauthId);
        }

        Member member = createOrUpdateMember(provider, socialResponse, userInfo, existingMember);
        memberRepository.save(member);
        boolean isFirstLogin = member.getSocialAccount(provider.getValue()).isFirst();

        return createSuccessfulLoginResponse(member, isFirstLogin, provider.getValue());
    }

    private boolean hasOtherAccounts(Member member) {
        // 다른 소셜 계정이 있거나 로컬 계정이 있으면 true
        return !member.getSocialAccounts().isEmpty() || member.getLocalAccount() != null;
    }

    private SocialLoginResponse handleConflictingSocialAccount(Member existingMember,
                                                               OAuthProvider provider,
                                                               UserInfo userInfo,
                                                               String oauthId) {
        log.info("다른 소셜 계정을 가진 기존 회원 발견: memberId={}, 시도한 제공자={}",
                existingMember.getId(), provider.getValue());

        // 소셜 연동을 위한 임시 토큰 생성
        String socialTemporaryToken = socialTemporaryTokenService.createSocialTemporaryToken(
                existingMember.getId(), 
                provider.getValue(), 
                oauthId, 
                userInfo.email()
        );

        UserProfile profile = createUserProfile(existingMember);

        return SocialLoginResponse.ofWithSocialToken(false, null, null, profile, socialTemporaryToken);
    }

    private Member createOrUpdateMember(OAuthProvider provider,
                                        SocialClientResponse socialResponse,
                                        UserInfo userInfo,
                                        Optional<Member> existingMember) {
        Member member = existingMember.orElseGet(() -> {
            log.info("신규 회원 생성: provider={}, phoneNumber={}, email={}",
                    provider.getValue(), userInfo.phoneNumber(), userInfo.email());
            return Member.createMember(MemberType.GUARDIAN, userInfo.name(), userInfo.phoneNumber());
        });

        addSocialAccountToMember(member, provider, socialResponse.oauthId(), userInfo.email(), socialResponse.refreshToken());
        logMemberCreationOrUpdate(member, provider, userInfo.email());

        return member;
    }

    private void addSocialAccountToMember(Member member, OAuthProvider provider, String oauthId, String email) {
        addSocialAccountToMember(member, provider, oauthId, email, null);
    }

    private void addSocialAccountToMember(Member member, OAuthProvider provider, String oauthId, String email, String refreshToken) {
        SocialAccount socialAccount = SocialAccount.createSocialAccount(email, provider.getValue(), oauthId, refreshToken, member);
        member.getSocialAccounts().add(socialAccount);
    }

    private void logMemberCreationOrUpdate(Member member, OAuthProvider provider, String email) {
        if (member.getId() != null) {
            log.info("기존 회원에 소셜 계정 추가: memberId={}, provider={}, email={}",
                    member.getId(), provider.getValue(), email);
        } else {
            log.info("신규 회원 생성 완료: provider={}, email={}", provider.getValue(), email);
        }
    }

    private Optional<Member> findMemberByProvider(OAuthProvider provider, String oauthId) {
        return memberRepository.findByProviderAndOauthId(provider.getValue(), oauthId);
    }

    private Member findAppleMemberByEmail(String email) {
        return memberRepository.findBySocialAccounts_EmailAndSocialAccounts_Provider(
                        email, OAuthProvider.APPLE.getValue())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Optional<Member> findExistingMemberByUserInfo(UserInfo userInfo) {
        if (userInfo.hasPhoneNumber()) {
            Optional<Member> member = memberRepository.findByPhoneNumber(userInfo.phoneNumber());
            if (member.isPresent()) {
                log.info("전화번호로 기존 회원 발견: phoneNumber={}", userInfo.phoneNumber());
                return member;
            }
        }

        if (userInfo.hasEmail()) {
            Optional<Member> member = memberRepository.findBySocialAccounts_Email(userInfo.email());
            if (member.isPresent()) {
                log.info("이메일로 기존 회원 발견: email={}", userInfo.email());
                return member;
            }
        }

        return Optional.empty();
    }

    private SocialLoginResponse createSuccessfulLoginResponse(Member member, boolean isFirstLogin, String currentProvider) {
        TokenPairResponse tokenPair = generateTokenPair(member, currentProvider);
        UserProfile profile = createUserProfile(member);

        log.info("소셜 로그인 성공: memberId={}, 최초로그인={}, provider={}",
                member.getId(), isFirstLogin, currentProvider);
        return SocialLoginResponse.of(isFirstLogin, tokenPair.accessToken(), tokenPair.refreshToken(), profile);
    }

    private TokenPairResponse generateTokenPair(Member member, String currentProvider) {
        return jwtTokenProvider.generateTokenPair(member.getId(), MemberRole.USER, currentProvider);
    }

    private UserProfile createUserProfile(Member member) {
        Member fullMember = memberRepository.findById(member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<String> providers = new ArrayList<>(fullMember.getSocialAccounts().stream()
                .map(SocialAccount::getProvider)
                .toList());

        // 로컬 계정이 있으면 LOCAL 추가
        if (fullMember.getLocalAccount() != null) {
            providers.add(PROVIDER_LOCAL);
        }

        String email = fullMember.getSocialAccounts().stream()
                .map(SocialAccount::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse(null);

        return UserProfile.of(member.getName(), member.getPhoneNumber(), email, providers);
    }
}
