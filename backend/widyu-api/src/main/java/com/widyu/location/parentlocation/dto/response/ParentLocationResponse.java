package com.widyu.location.parentlocation.dto.response;

import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ParentLocationResponse {
    private Long parentLocationId;
    private Long memberId;
    private String memberName;
    private LocationType locationType;
    private String placeAddress;
    private Double latitude;
    private Double longitude;

    public static ParentLocationResponse of(ParentLocation parentLocation) {
        return ParentLocationResponse.builder()
            .parentLocationId(parentLocation.getId())
            .memberId(parentLocation.getMember().getId())
            .memberName(parentLocation.getMember().getName())
            .locationType(parentLocation.getLocationType())
            .placeAddress(parentLocation.getPlaceAddress())
            .latitude(parentLocation.getLatitude())
            .longitude(parentLocation.getLongitude())
            .build();
    }
}
