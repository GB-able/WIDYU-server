package com.widyu.location.parentlocation.dto.response;

import com.widyu.member.Member;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SeniorWithLocationsResponse {
    private Long memberId;
    private String memberName;
    private List<LocationInfo> locations;

    public static SeniorWithLocationsResponse of(Member senior, List<LocationInfo> locations) {
        return SeniorWithLocationsResponse.builder()
            .memberId(senior.getId())
            .memberName(senior.getName())
            .locations(locations)
            .build();
    }
}
