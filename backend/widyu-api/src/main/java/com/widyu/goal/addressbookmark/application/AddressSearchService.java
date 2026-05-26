package com.widyu.goal.addressbookmark.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.goal.addressbookmark.client.JusoApiClient;
import com.widyu.goal.addressbookmark.dto.external.JusoApiResponse;
import com.widyu.goal.addressbookmark.dto.response.AddressSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddressSearchService {

    private final JusoApiClient jusoApiClient;

    @Value("${juso.api.confm-key}")
    private String confmKey;

    public AddressSearchResponse search(String keyword, int page, int size) {
        JusoApiResponse response = jusoApiClient.searchAddress(confmKey, keyword, page, size, "json");

        JusoApiResponse.Common common = response.results().common();
        if (!"0".equals(common.errorCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "주소 검색 실패: " + common.errorMessage());
        }

        return AddressSearchResponse.of(response);
    }
}
