package com.widyu.mypage.dto.response;

public record SeniorInfoResponse(
        Long memberId,
        String profileImage,
        String name,
        Long points
) {
    public static SeniorInfoResponse of(Long memberId, String profileImage, String name, Long points) {
        return new SeniorInfoResponse(memberId, profileImage, name, points);
    }
}
