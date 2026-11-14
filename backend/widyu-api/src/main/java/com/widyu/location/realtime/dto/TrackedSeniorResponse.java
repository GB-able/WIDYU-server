package com.widyu.location.realtime.dto;

import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import lombok.Builder;

/** 보호자의 추적 대상 목록 한 줄. 좌표의 신선도 필드는 {@link LocationUpdateResponse}와 같은 뜻이다(LLD-0141). */
@Builder
public record TrackedSeniorResponse(
        Long memberId,
        String name,
        String profileImage,
        Double latitude,
        Double longitude,
        Long measuredAtMs,
        Long receivedAtMs,
        LocationSource locationSource,
        Long ageSeconds
) {
    public static TrackedSeniorResponse of(SeniorProfile seniorProfile, Double latitude, Double longitude,
                                           LocationFreshness freshness) {
        Member seniorMember = seniorProfile.getMember();
        return TrackedSeniorResponse.builder()
                .memberId(seniorMember.getId())
                .name(seniorMember.getName())
                .profileImage(seniorMember.getProfileImage())
                .latitude(latitude)
                .longitude(longitude)
                .measuredAtMs(freshness.measuredAtMs())
                .receivedAtMs(freshness.receivedAtMs())
                .locationSource(freshness.source())
                .ageSeconds(freshness.ageSeconds())
                .build();
    }

    /** 위치가 전혀 없는 시니어. */
    public static TrackedSeniorResponse withoutLocation(SeniorProfile seniorProfile) {
        Member seniorMember = seniorProfile.getMember();
        return TrackedSeniorResponse.builder()
                .memberId(seniorMember.getId())
                .name(seniorMember.getName())
                .profileImage(seniorMember.getProfileImage())
                .build();
    }
}
