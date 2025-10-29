package com.widyu.member.dto.response;

import com.widyu.member.SeniorProfile;

public record SeniorPointsResponse(
        Long points
) {
    public static SeniorPointsResponse from(SeniorProfile seniorProfile) {
        return new SeniorPointsResponse(seniorProfile.getPoints());
    }
}
