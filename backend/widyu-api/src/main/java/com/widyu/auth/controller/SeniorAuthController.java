package com.widyu.auth.controller;

import com.widyu.auth.application.senior.SeniorAuthService;
import com.widyu.auth.controller.docs.SeniorAuthDocs;
import com.widyu.auth.dto.request.SeniorSignInRequest;
import com.widyu.auth.dto.request.SeniorSignUpRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/seniors")
public class SeniorAuthController implements SeniorAuthDocs {

    private final SeniorAuthService seniorAuthService;

    @PostMapping("/sign-up")
    public ApiResponseTemplate<Void> seniorSignUpBulk(@RequestBody @Valid List<SeniorSignUpRequest> requests) {
        seniorAuthService.seniorSignUpBulk(requests);
        return ApiResponseTemplate.ok()
                .code("AUTH_3001")
                .message("시니어 일괄 회원가입이 성공적으로 완료되었습니다.")
                .build();
    }

    @PostMapping("/sign-in")
    public ApiResponseTemplate<TokenPairResponse> seniorSignIn(@RequestBody @Valid SeniorSignInRequest request) {
        TokenPairResponse response = seniorAuthService.seniorSignIn(request);
        return ApiResponseTemplate.ok()
                .code("AUTH_3002")
                .message("시니어 로그인 성공")
                .body(response);
    }
}
