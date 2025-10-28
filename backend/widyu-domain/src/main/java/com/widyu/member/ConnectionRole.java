package com.widyu.member;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConnectionRole {
    CHILD("자녀"),
    SPOUSE("배우자"),
    CAREGIVER("간병인"),
    NURSE("요양보호사"),
    SOCIAL_WORKER("사회복지사"),
    OTHER("기타");

    private final String description;
}