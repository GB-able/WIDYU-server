package com.widyu.member;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MemberRole {
    ADMIN("ROLE_ADMIN"),
    USER("ROLE_USER"),
    TEMPORARY("ROLE_TEMPORARY"),
    ;

    private final String value;
}
