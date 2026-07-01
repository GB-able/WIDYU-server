package com.widyu.goal.addressbookmark.client;

import com.widyu.goal.addressbookmark.dto.external.KakaoGeocodingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "kakaoGeocodingClient",
        url = "${kakao.geocoding.url}",
        configuration = KakaoGeocodingClientConfig.class
)
public interface KakaoGeocodingClient {

    @GetMapping("/v2/local/search/address.json")
    KakaoGeocodingResponse geocode(@RequestParam("query") String query);
}
