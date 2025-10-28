package com.widyu.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberType {
    SENIOR("시니어"),      // widyu 앱 사용자 (기존 PARENT)
    GUARDIAN("보호자"),    // widyu-care 앱 사용자
    PARENT("부모");        // 하위 호환성을 위해 유지 (SENIOR와 동일)

    private final String description;
}

