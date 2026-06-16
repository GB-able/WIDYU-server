package com.widyu.home.dto.response;

import com.widyu.member.SeniorProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GuardianSeniorListResponse(
        @Schema(description = "가족에 속한 시니어 목록")
        List<SeniorItem> seniors
) {
    public record SeniorItem(
            @Schema(description = "시니어 회원 ID", example = "42")
            Long memberId,

            @Schema(description = "시니어 이름", example = "김영희")
            String name,

            @Schema(description = "시니어 프로필 이미지 URL", example = "https://cdn.widyu.shop/profiles/senior.png")
            String profileImage
    ) {
        public static SeniorItem from(SeniorProfile seniorProfile) {
            return new SeniorItem(
                    seniorProfile.getMember().getId(),
                    seniorProfile.getMember().getName(),
                    seniorProfile.getMember().getProfileImage()
            );
        }
    }

    public static GuardianSeniorListResponse from(List<SeniorProfile> seniors) {
        return new GuardianSeniorListResponse(
                seniors.stream()
                        .map(SeniorItem::from)
                        .toList()
        );
    }

    public static GuardianSeniorListResponse empty() {
        return new GuardianSeniorListResponse(List.of());
    }
}
