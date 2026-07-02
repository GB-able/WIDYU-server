package com.widyu.goal.addressbookmark.dto.response;

import com.widyu.goal.addressbookmark.dto.external.KakaoGeocodingResponse;

public record GeocodingResponse(
        Double latitude,
        Double longitude
) {
    public static GeocodingResponse from(KakaoGeocodingResponse.Document document) {
        return new GeocodingResponse(
                Double.parseDouble(document.latitude()),
                Double.parseDouble(document.longitude())
        );
    }
}
