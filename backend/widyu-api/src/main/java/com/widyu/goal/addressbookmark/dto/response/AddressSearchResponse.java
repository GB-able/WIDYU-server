package com.widyu.goal.addressbookmark.dto.response;

import com.widyu.goal.addressbookmark.dto.external.JusoApiResponse;
import java.util.List;

public record AddressSearchResponse(
        List<AddressItem> addresses,
        int totalCount,
        int currentPage,
        int countPerPage
) {
    public record AddressItem(
            String roadAddr,
            String jibunAddr,
            String zipNo,
            String bdNm,
            String siNm,
            String sggNm,
            String emdNm
    ) {}

    public static AddressSearchResponse of(JusoApiResponse apiResponse) {
        JusoApiResponse.Common common = apiResponse.results().common();
        List<JusoApiResponse.JusoItem> jusoItems = apiResponse.results().juso();

        List<AddressItem> addresses = (jusoItems == null) ? List.of() : jusoItems.stream()
                .map(j -> new AddressItem(
                        j.roadAddr(),
                        j.jibunAddr(),
                        j.zipNo(),
                        j.bdNm(),
                        j.siNm(),
                        j.sggNm(),
                        j.emdNm()
                ))
                .toList();

        return new AddressSearchResponse(
                addresses,
                parseIntSafe(common.totalCount()),
                parseIntSafe(common.currentPage()),
                parseIntSafe(common.countPerPage())
        );
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
