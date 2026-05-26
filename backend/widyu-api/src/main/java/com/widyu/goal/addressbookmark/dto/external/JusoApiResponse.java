package com.widyu.goal.addressbookmark.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JusoApiResponse(Results results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results(Common common, List<JusoItem> juso) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Common(
            String totalCount,
            String currentPage,
            String countPerPage,
            String errorCode,
            String errorMessage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JusoItem(
            String roadAddr,
            String roadAddrPart1,
            String roadAddrPart2,
            String jibunAddr,
            String engAddr,
            String zipNo,
            String bdNm,
            String bdKdcd,
            String siNm,
            String sggNm,
            String emdNm,
            String liNm,
            String rn,
            String udrtYn,
            Integer buldMnnm,
            Integer buldSlno,
            String mtYn
    ) {}
}
