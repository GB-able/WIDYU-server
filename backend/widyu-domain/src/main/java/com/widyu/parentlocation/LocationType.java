package com.widyu.parentlocation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LocationType {
    HOME("집"),
    OTHER("기타");

    private final String description;
}
