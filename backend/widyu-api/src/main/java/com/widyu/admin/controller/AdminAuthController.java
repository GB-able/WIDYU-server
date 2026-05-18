package com.widyu.admin.controller;

import com.widyu.admin.application.AdminAuthService;
import com.widyu.admin.dto.response.AdminLoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public AdminLoginResponse login(@RequestBody Map<String, String> request) {
        return AdminLoginResponse.of(
                adminAuthService.login(request.get("email"), request.get("password"))
        );
    }
}
