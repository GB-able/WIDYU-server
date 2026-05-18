package com.widyu.mypage.dto.response;

import com.widyu.member.SeniorProfile;
import java.util.List;

public record ConnectedSeniorResponse(
        List<SeniorItem> seniors
) {
    public record SeniorItem(
            Long memberId,
            String profileImage,
            String name
    ) {
        public static SeniorItem from(SeniorProfile seniorProfile) {
            return new SeniorItem(
                    seniorProfile.getMember().getId(),
                    seniorProfile.getMember().getProfileImage(),
                    seniorProfile.getMember().getName()
            );
        }
    }

    public static ConnectedSeniorResponse from(List<SeniorProfile> seniors) {
        return new ConnectedSeniorResponse(
                seniors.stream().map(SeniorItem::from).toList()
        );
    }
}
