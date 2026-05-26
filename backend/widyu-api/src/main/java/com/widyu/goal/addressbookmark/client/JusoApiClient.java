package com.widyu.goal.addressbookmark.client;

import com.widyu.goal.addressbookmark.dto.external.JusoApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "jusoApiClient",
        url = "${juso.api.url}",
        configuration = JusoApiClientConfig.class
)
public interface JusoApiClient {

    @GetMapping("/addrLinkApi.do")
    JusoApiResponse searchAddress(
            @RequestParam("confmKey") String confmKey,
            @RequestParam("keyword") String keyword,
            @RequestParam("currentPage") int currentPage,
            @RequestParam("countPerPage") int countPerPage,
            @RequestParam("resultType") String resultType
    );
}
