package com.widyu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.auth.TemporaryMember;
import com.widyu.auth.VerificationCode;
import com.widyu.auth.dto.response.TemporaryTokenResponse;
import com.widyu.auth.repository.TemporaryMemberRepository;
import com.widyu.auth.repository.VerificationCodeRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VerificationCodeService 단위 테스트")
class VerificationCodeServiceTest {

    @Mock private VerificationCodeRepository verificationCodeRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TemporaryMemberRepository temporaryMemberRepository;

    @InjectMocks
    private VerificationCodeService verificationCodeService;

    @Test
    @DisplayName("올바른 인증 코드 입력 시 임시 토큰을 반환한다")
    void 올바른_인증코드_입력_시_임시토큰을_반환한다() {
        // given
        String phone = "01012345678";
        String code = "123456";
        String name = "홍길동";

        VerificationCode verificationCode = VerificationCode.builder()
                .phoneNumber(phone).code(code).name(name).ttl(300).build();
        TemporaryMember tempMember = TemporaryMember.createTemporaryMember(name, phone);
        TemporaryTokenResponse expectedResponse = new TemporaryTokenResponse("temp-token-value");

        given(verificationCodeRepository.findById(phone)).willReturn(Optional.of(verificationCode));
        given(temporaryMemberRepository.save(any(TemporaryMember.class))).willReturn(tempMember);
        given(jwtTokenProvider.generateTemporaryToken(any(TemporaryMember.class))).willReturn(expectedResponse);

        // when
        TemporaryTokenResponse result = verificationCodeService.verifyAndIssueTemporaryToken(phone, code);

        // then
        assertThat(result).isEqualTo(expectedResponse);
        verify(temporaryMemberRepository).save(any(TemporaryMember.class));
    }

    @Test
    @DisplayName("인증 코드 불일치 시 BusinessException을 던진다")
    void 인증코드_불일치_시_예외가_발생한다() {
        // given
        String phone = "01012345678";
        VerificationCode verificationCode = VerificationCode.builder()
                .phoneNumber(phone).code("123456").name("홍길동").ttl(300).build();
        given(verificationCodeRepository.findById(phone)).willReturn(Optional.of(verificationCode));

        // when & then
        assertThatThrownBy(() -> verificationCodeService.verifyAndIssueTemporaryToken(phone, "999999"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_VERIFICATION_CODE_MISMATCH);
    }

    @Test
    @DisplayName("인증 코드가 존재하지 않으면 BusinessException을 던진다")
    void 인증코드가_존재하지_않으면_예외가_발생한다() {
        // given
        String phone = "01012345678";
        given(verificationCodeRepository.findById(phone)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> verificationCodeService.verifyAndIssueTemporaryToken(phone, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_VERIFICATION_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("인증 성공 후 인증 코드를 삭제한다")
    void 인증_성공_후_인증코드를_삭제한다() {
        // given
        String phone = "01012345678";
        String code = "123456";
        VerificationCode verificationCode = VerificationCode.builder()
                .phoneNumber(phone).code(code).name("홍길동").ttl(300).build();
        TemporaryMember tempMember = TemporaryMember.createTemporaryMember("홍길동", phone);

        given(verificationCodeRepository.findById(phone)).willReturn(Optional.of(verificationCode));
        given(temporaryMemberRepository.save(any())).willReturn(tempMember);
        given(jwtTokenProvider.generateTemporaryToken(any())).willReturn(new TemporaryTokenResponse("token"));

        // when
        verificationCodeService.verifyAndIssueTemporaryToken(phone, code);

        // then
        verify(verificationCodeRepository).deleteById(phone);
    }

    @Test
    @DisplayName("인증 코드 불일치 시 임시 회원을 생성하지 않는다")
    void 인증코드_불일치_시_임시회원을_생성하지_않는다() {
        // given
        String phone = "01012345678";
        VerificationCode verificationCode = VerificationCode.builder()
                .phoneNumber(phone).code("123456").name("홍길동").ttl(300).build();
        given(verificationCodeRepository.findById(phone)).willReturn(Optional.of(verificationCode));

        // when
        assertThatThrownBy(() -> verificationCodeService.verifyAndIssueTemporaryToken(phone, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_VERIFICATION_CODE_MISMATCH);

        // then
        verify(temporaryMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("생성된 임시 회원에 전화번호와 이름이 포함된다")
    void 생성된_임시회원에_전화번호와_이름이_포함된다() {
        // given
        String phone = "01012345678";
        String code = "123456";
        String name = "홍길동";
        VerificationCode verificationCode = VerificationCode.builder()
                .phoneNumber(phone).code(code).name(name).ttl(300).build();
        TemporaryMember tempMember = TemporaryMember.createTemporaryMember(name, phone);

        given(verificationCodeRepository.findById(phone)).willReturn(Optional.of(verificationCode));
        given(temporaryMemberRepository.save(any(TemporaryMember.class))).willReturn(tempMember);
        given(jwtTokenProvider.generateTemporaryToken(any())).willReturn(new TemporaryTokenResponse("token"));

        // when
        verificationCodeService.verifyAndIssueTemporaryToken(phone, code);

        // then
        verify(temporaryMemberRepository).save(
                org.mockito.ArgumentMatchers.argThat(tm ->
                        tm.getPhoneNumber().equals(phone) && tm.getName().equals(name)
                )
        );
    }
}
