package com.widyu.location.parentlocation.dto.response;

import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LocationInfo {
    private Long parentLocationId;
    private LocationType locationType;
    private String placeAddress;
    private String latitude;
    private String longitude;
    private String name;

    public static LocationInfo of(ParentLocation parentLocation) {
        return LocationInfo.builder()
            .parentLocationId(parentLocation.getId())
            .locationType(parentLocation.getLocationType())
            .placeAddress(parentLocation.getPlaceAddress())
            .latitude(parentLocation.getLatitude())
            .longitude(parentLocation.getLongitude())
            .name(parentLocation.getName())
            .build();
    }
}
