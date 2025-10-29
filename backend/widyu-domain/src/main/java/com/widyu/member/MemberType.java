package com.widyu.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberType {
    SENIOR("시니어"),      // widyu 앱 사용자
    GUARDIAN("보호자");    // widyu-care 앱 사용자
    private final String description;
}

