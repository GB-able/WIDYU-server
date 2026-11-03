package com.widyu.auth.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.widyu.auth.application.senior.SeniorAuthService;
import com.widyu.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@DisplayName("시니어 일괄 가입 요청의 요소별 검증 (#951)")
class SeniorSignUpBulkValidationTest {

    private final SeniorAuthService seniorAuthService = mock(SeniorAuthService.class);

    private MockMvc mockMvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new SeniorAuthController(seniorAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("일괄 가입에서 한 시니어가 위도만 보내면 400이고 가입을 진행하지 않는다")
    void 일괄_가입에서_위도만_보내면_400이다() throws Exception {
        // given
        String body = """
                [{"name":"김부모","birthDate":"1955-03-15","phoneNumber":"01012345678",
                  "address":"서울특별시 마포구 성암로 301","inviteCode":"1234567","latitude":37.5796}]
                """;

        // when / then
        mockMvc().perform(post("/api/v1/auth/seniors/sign-up").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(seniorAuthService, never()).seniorSignUpBulk(anyList());
    }

    @Test
    @DisplayName("일괄 가입에서 위도가 범위를 벗어나면 400이고 가입을 진행하지 않는다")
    void 일괄_가입에서_위도가_범위를_벗어나면_400이다() throws Exception {
        // given
        String body = """
                [{"name":"김부모","birthDate":"1955-03-15","phoneNumber":"01012345678",
                  "address":"서울특별시 마포구 성암로 301","inviteCode":"1234567","latitude":91.0,"longitude":126.89}]
                """;

        // when / then
        mockMvc().perform(post("/api/v1/auth/seniors/sign-up").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(seniorAuthService, never()).seniorSignUpBulk(anyList());
    }

    @Test
    @DisplayName("일괄 가입에서 좌표를 함께 보내면 가입을 진행한다")
    void 일괄_가입에서_좌표를_함께_보내면_진행한다() throws Exception {
        // given
        String body = """
                [{"name":"김부모","birthDate":"1955-03-15","phoneNumber":"01012345678",
                  "address":"서울특별시 마포구 성암로 301","inviteCode":"1234567","latitude":37.5796,"longitude":126.8924}]
                """;

        // when / then
        mockMvc().perform(post("/api/v1/auth/seniors/sign-up").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(seniorAuthService).seniorSignUpBulk(anyList());
    }
}
