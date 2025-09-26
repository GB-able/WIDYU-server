package com.widyu.domain.member.dto.response;

import com.widyu.domain.member.entity.ParentProfile;

public record ParentPointsResponse(
        Long points
) {
    public static ParentPointsResponse from(ParentProfile parentProfile) {
        return new ParentPointsResponse(parentProfile.getPoints());
    }
}