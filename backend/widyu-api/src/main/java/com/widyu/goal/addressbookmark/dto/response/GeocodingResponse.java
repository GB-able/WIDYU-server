package com.widyu.goal.addressbookmark.dto.response;

import com.widyu.goal.addressbookmark.dto.external.KakaoGeocodingResponse;

public record GeocodingResponse(
        String latitude,
        String longitude
) {
    public static GeocodingResponse from(KakaoGeocodingResponse.Document document) {
        return new GeocodingResponse(document.latitude(), document.longitude());
    }
}
