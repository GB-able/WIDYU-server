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

    @Value("${oauth.kakao.client-id}")
    private String clientId;

    public GeocodingResponse geocode(String address) {
        KakaoGeocodingResponse response;
        try {
            response = kakaoGeocodingClient.geocode("KakaoAK " + clientId, address);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "주소 좌표 변환 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (response.documents() == null || response.documents().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "입력한 주소의 좌표를 찾을 수 없습니다. 정확한 도로명주소를 입력해 주세요.");
        }

        return GeocodingResponse.from(response.documents().get(0));
    }
}
