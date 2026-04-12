package com.widyu.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.widyu.auth.VerificationCode;
import com.widyu.auth.repository.VerificationCodeRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.CoolsmsProperties;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsService 단위 테스트")
class SmsServiceTest {

    @Mock
    private VerificationCodeRepository verificationCodeRepository;

    private SmsService smsService;
    private DefaultMessageService mockMessageService;

    private static final CoolsmsProperties TEST_PROPERTIES = new CoolsmsProperties(
            "test-api-key",
            "test-api-secret",
            "https://api.coolsms.co.kr",
            "01000000000",
            6,
            300,
            "인증번호: {code}"
    );

    @BeforeEach
    void setUp() {
        smsService = new SmsService(TEST_PROPERTIES, verificationCodeRepository);
        mockMessageService = mock(DefaultMessageService.class);
        ReflectionTestUtils.setField(smsService, "messageService", mockMessageService);
    }

    @Test
    @DisplayName("유효한 전화번호로 SMS 전송 시 인증 코드를 저장하고 메시지를 전송한다")
    void sendVerificationSms_validPhone_savesCodeAndSendsMessage() throws Exception {
        smsService.sendVerificationSms("01012345678", "홍길동");

        verify(verificationCodeRepository).save(any(VerificationCode.class));
        verify(mockMessageService).send(any(Message.class));
    }

    @Test
    @DisplayName("전화번호가 null이면 BusinessException을 던진다")
    void sendVerificationSms_nullPhone_throwsBusinessException() {
        assertThatThrownBy(() -> smsService.sendVerificationSms(null, "홍길동"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_NUMBER_REQUIRED);
    }

    @Test
    @DisplayName("전화번호가 빈 문자열이면 BusinessException을 던진다")
    void sendVerificationSms_emptyPhone_throwsBusinessException() {
        assertThatThrownBy(() -> smsService.sendVerificationSms("   ", "홍길동"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_NUMBER_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0201234567", "1234567890", "010123456789", "abc", "01023456"})
    @DisplayName("유효하지 않은 전화번호 형식이면 BusinessException을 던진다")
    void sendVerificationSms_invalidPhoneFormat_throwsBusinessException(String phone) {
        assertThatThrownBy(() -> smsService.sendVerificationSms(phone, "홍길동"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PHONE_NUMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01012345678", "01112345678", "01612345678", "01712345678", "01812345678", "01912345678"})
    @DisplayName("01X로 시작하는 유효한 전화번호 형식은 정상 처리된다")
    void sendVerificationSms_validPhoneFormats_success(String phone) throws Exception {
        smsService.sendVerificationSms(phone, "홍길동");
        verify(mockMessageService).send(any(Message.class));
    }

    @Test
    @DisplayName("SMS 전송 중 일반 예외 발생 시 BusinessException을 던진다")
    void sendVerificationSms_generalException_throwsBusinessException() throws Exception {
        doThrow(new RuntimeException("네트워크 오류")).when(mockMessageService).send(any(Message.class));

        assertThatThrownBy(() -> smsService.sendVerificationSms("01012345678", "홍길동"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SMS_SEND_FAILED);
    }

    @Test
    @DisplayName("저장되는 VerificationCode에 전화번호와 이름이 포함된다")
    void sendVerificationSms_savedVerificationCodeContainsPhoneAndName() throws Exception {
        String phone = "01099998888";
        String name = "김철수";

        smsService.sendVerificationSms(phone, name);

        verify(verificationCodeRepository).save(
                org.mockito.ArgumentMatchers.argThat(
                        vc -> vc.getPhoneNumber().equals(phone) && vc.getName().equals(name)
                )
        );
    }
}
