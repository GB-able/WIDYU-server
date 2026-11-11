package com.widyu.location.access.dto.response;

/** 위치 열람 기록 CSV 한 개(LLD-0132). 컨트롤러가 첨부 파일 응답으로 바꾼다. */
public record LocationAccessExportFile(String fileName, byte[] bytes) {

    public static LocationAccessExportFile of(String fileName, byte[] bytes) {
        return new LocationAccessExportFile(fileName, bytes);
    }
}
