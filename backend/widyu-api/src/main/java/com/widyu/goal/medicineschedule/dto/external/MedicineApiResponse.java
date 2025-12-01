package com.widyu.goal.medicineschedule.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 공공데이터포털 의약품 API 응답
 * API 문서: IROS_239_의약품개요정보(e약은요) 서비스_v1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedicineApiResponse(
        @JsonProperty("header")
        Header header,

        @JsonProperty("body")
        Body body
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(
            @JsonProperty("resultCode")
            String resultCode,

            @JsonProperty("resultMsg")
            String resultMsg
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            @JsonProperty("pageNo")
            Integer pageNo,

            @JsonProperty("totalCount")
            Integer totalCount,

            @JsonProperty("numOfRows")
            Integer numOfRows,

            @JsonProperty("items")
            List<MedicineItem> items,

            @JsonProperty("item")  // 단일 결과일 때 "item"으로 올 수 있음
            List<MedicineItem> item
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MedicineItem(
            @JsonProperty("entpName")
            String entpName,

            @JsonProperty("itemName")
            String itemName,

            @JsonProperty("itemSeq")
            String itemSeq,

            @JsonProperty("efficacy")
            String efcyQesitm,

            @JsonProperty("useMethodQesitm")
            String useMethodQesitm,

            @JsonProperty("itemImage")
            String itemImage
    ) {}
}
