package com.widyu.goal.home.dto.response;

import java.util.List;

public record FamilyListResponse(
        List<FamilyMemberResponse> families
) {
    public static FamilyListResponse of(List<FamilyMemberResponse> families) {
        return new FamilyListResponse(families);
    }
}
