package com.widyu.member.dto.response;

import com.widyu.member.ParentProfile;

public record ParentPointsResponse(
        Long points
) {
    public static ParentPointsResponse from(ParentProfile parentProfile) {
        return new ParentPointsResponse(parentProfile.getPoints());
    }
}