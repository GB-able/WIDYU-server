package com.widyu.auth.application.guardian.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.widyu.auth.TemporaryMember;
import com.widyu.auth.dto.request.EmailCheckRequest;
import com.widyu.auth.dto.request.LocalGuardianSignInRequest;
import com.widyu.auth.dto.response.LocalSignupResponse;
import com.widyu.auth.dto.response.SignUpUserInfo;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.TemporaryMemberUtil;
import com.widyu.member.LocalAccount;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.LocalAccountRepository;
import com.widyu.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalLoginService 단위 테스트")
class LocalLoginServiceTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private MemberRepository memberRepository;
    @Mock private LocalAccountRepository localAccountRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TemporaryMemberUtil temporaryMemberUtil;

    @InjectMocks
    private LocalLoginService localLoginService;

    @Test
    @DisplayName("신규 이메일로 회원가입 시 새 멤버를 생성하고 토큰을 반환한다")
    void signupGuardianWithLocal_newEmail_createsNewMemberAndReturnsToken() {
        // given
        TemporaryMember temp = TemporaryMember.createTemporaryMember("홍길동", "01012345678");
        String email = "test@test.com";
        String rawPassword = "password123!";
        Member newMember = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(newMember, "id", 1L);
        TokenPairResponse tokenPair = TokenPairResponse.of(1L, "access-token", "refresh-token");

        when(localAccountRepository.existsByEmail(email)).thenReturn(false);
        when(memberRepository.findByPhoneNumberAndName("01012345678", "홍길동")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenReturn(newMember);
        when(passwordEncoder.encode(rawPassword)).thenReturn("encoded-password");
        when(localAccountRepository.save(any(LocalAccount.class))).thenReturn(
                LocalAccount.createLocalAccount(newMember, email, "encoded-password")
        );
        when(jwtTokenProvider.generateTokenPair(any(), any(), any())).thenReturn(tokenPair);

        // when
        LocalSignupResponse result = localLoginService.signupGuardianWithLocal(temp, email, rawPassword);

        // then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(memberRepository).save(any(Member.class));
        verify(localAccountRepository).save(any(LocalAccount.class));
    }

    @Test
    @DisplayName("기존 전화번호+이름의 멤버가 있으면 새 멤버를 생성하지 않고 재사용한다")
    void signupGuardianWithLocal_existingMemberByPhone_reusesExistingMember() {
        // given
        TemporaryMember temp = TemporaryMember.createTemporaryMember("홍길동", "01012345678");
        String email = "new@test.com";
        Member existingMember = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(existingMember, "id", 1L);
        TokenPairResponse tokenPair = TokenPairResponse.of(1L, "access", "refresh");

        when(localAccountRepository.existsByEmail(email)).thenReturn(false);
        when(memberRepository.findByPhoneNumberAndName("01012345678", "홍길동"))
                .thenReturn(Optional.of(existingMember));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(localAccountRepository.save(any())).thenReturn(
                LocalAccount.createLocalAccount(existingMember, email, "encoded")
        );
        when(jwtTokenProvider.generateTokenPair(any(), any(), any())).thenReturn(tokenPair);

        // when
        localLoginService.signupGuardianWithLocal(temp, email, "password");

        // then
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("이미 등록된 이메일로 회원가입 시 BusinessException을 던진다")
    void signupGuardianWithLocal_duplicateEmail_throwsBusinessException() {
        // given
        TemporaryMember temp = TemporaryMember.createTemporaryMember("홍길동", "01012345678");
        String email = "existing@test.com";
        when(localAccountRepository.existsByEmail(email)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> localLoginService.signupGuardianWithLocal(temp, email, "password"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REGISTERED_EMAIL);
    }

    @Test
    @DisplayName("등록되지 않은 이메일 확인 시 true를 반환한다")
    void isEmailRegistered_notRegistered_returnsTrue() {
        when(localAccountRepository.existsByEmail("new@test.com")).thenReturn(false);

        boolean result = localLoginService.isEmailRegistered(new EmailCheckRequest("new@test.com"));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("이미 등록된 이메일 확인 시 false를 반환한다")
    void isEmailRegistered_registered_returnsFalse() {
        when(localAccountRepository.existsByEmail("existing@test.com")).thenReturn(true);

        boolean result = localLoginService.isEmailRegistered(new EmailCheckRequest("existing@test.com"));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인 시 토큰 쌍을 반환한다")
    void signIn_validCredentials_returnsTokenPair() {
        // given
        String email = "test@test.com";
        String rawPassword = "password123!";
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        LocalAccount localAccount = LocalAccount.createLocalAccount(member, email, "encoded-password");
        TokenPairResponse expectedToken = TokenPairResponse.of(1L, "access", "refresh");

        when(localAccountRepository.findByEmail(email)).thenReturn(Optional.of(localAccount));
        when(passwordEncoder.matches(rawPassword, "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.generateTokenPair(any(), any(), any())).thenReturn(expectedToken);

        // when
        TokenPairResponse result = localLoginService.signIn(new LocalGuardianSignInRequest(email, rawPassword));

        // then
        assertThat(result).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("등록되지 않은 이메일로 로그인 시 BusinessException을 던진다")
    void signIn_emailNotFound_throwsBusinessException() {
        when(localAccountRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> localLoginService.signIn(
                new LocalGuardianSignInRequest("notfound@test.com", "password")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_EMAIL);
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시 BusinessException을 던진다")
    void signIn_wrongPassword_throwsBusinessException() {
        // given
        String email = "test@test.com";
        Member member = Member.createMember(MemberType.GUARDIAN, "홍길동", "01012345678");
        LocalAccount localAccount = LocalAccount.createLocalAccount(member, email, "encoded-password");

        when(localAccountRepository.findByEmail(email)).thenReturn(Optional.of(localAccount));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> localLoginService.signIn(new LocalGuardianSignInRequest(email, "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);
    }
}
