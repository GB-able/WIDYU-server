package com.widyu.goal.addressbookmark.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.goal.addressbookmark.client.KakaoGeocodingClient;
import com.widyu.goal.addressbookmark.dto.external.KakaoGeocodingResponse;
import com.widyu.goal.addressbookmark.dto.response.GeocodingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final KakaoGeocodingClient kakaoGeocodingClient;

    @Value("${oauth.kakao.admin-key:}")
    private String adminKey;

    public GeocodingResponse geocode(String address) {
        KakaoGeocodingResponse response = kakaoGeocodingClient.geocode("KakaoAK " + adminKey, address);

        if (response.documents() == null || response.documents().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "좌표를 찾을 수 없는 주소입니다.");
        }

        return GeocodingResponse.from(response.documents().get(0));
    }
}
