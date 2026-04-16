package com.widyu.mypage.dto.response;

import com.widyu.member.FamilyConnection;
import java.util.List;

public record ConnectedSeniorResponse(
        List<SeniorItem> seniors
) {
    public record SeniorItem(
            Long memberId,
            String profileImage,
            String name
    ) {
        public static SeniorItem from(FamilyConnection connection) {
            return new SeniorItem(
                    connection.getSenior().getMember().getId(),
                    connection.getSenior().getMember().getProfileImage(),
                    connection.getSenior().getMember().getName()
            );
        }
    }

    public static ConnectedSeniorResponse from(List<FamilyConnection> connections) {
        return new ConnectedSeniorResponse(
                connections.stream().map(SeniorItem::from).toList()
        );
    }
}
