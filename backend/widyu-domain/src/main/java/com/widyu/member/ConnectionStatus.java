package com.widyu.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConnectionStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    PENDING("대기중"),
    REJECTED("거절됨");

    private final String description;
}