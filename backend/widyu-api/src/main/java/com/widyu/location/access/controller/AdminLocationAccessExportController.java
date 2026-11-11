package com.widyu.location.access.controller;

import com.widyu.location.access.application.LocationAccessExportService;
import com.widyu.location.access.controller.docs.AdminLocationAccessExportDocs;
import com.widyu.location.access.dto.response.LocationAccessExportFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 경로 변수 이름이 {@code memberId}라 관리자 접속기록에 대상 회원이 남는다(LLD-0057, LLD-0132). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/members/{memberId}/location-access-exports")
public class AdminLocationAccessExportController implements AdminLocationAccessExportDocs {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final LocationAccessExportService locationAccessExportService;

    @Override
    @GetMapping
    public ResponseEntity<byte[]> export(
            @PathVariable Long memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        LocationAccessExportFile file = locationAccessExportService.export(memberId, from, to);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.bytes());
    }
}
