package com.widyu.mypage.dto.response;

public record FamilyCodeResponse(
        String familyCode
) {
    public static FamilyCodeResponse of(String familyCode) {
        return new FamilyCodeResponse(familyCode);
    }
}
