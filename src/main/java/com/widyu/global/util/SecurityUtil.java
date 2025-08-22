package com.widyu.global.util;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;


@Component
public class SecurityUtil {

    public Long getCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            return Long.parseLong(authentication.getName());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public String getCurrentMemberRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            return authentication.getAuthorities()
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED))
                    .getAuthority();

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
