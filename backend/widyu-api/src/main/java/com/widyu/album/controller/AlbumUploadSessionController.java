package com.widyu.album.controller;

import com.widyu.album.application.AlbumUploadSessionFacade;
import com.widyu.album.controller.docs.AlbumUploadSessionDocs;
import com.widyu.album.dto.request.AlbumUploadCompleteRequest;
import com.widyu.album.dto.request.AlbumUploadSessionCreateRequest;
import com.widyu.album.dto.response.AlbumUploadAcceptedResponse;
import com.widyu.album.dto.response.AlbumUploadSessionResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/albums/uploads")
public class AlbumUploadSessionController implements AlbumUploadSessionDocs {

    private final AlbumUploadSessionFacade albumUploadSessionFacade;

    @Override
    @PostMapping
    public ApiResponseTemplate<AlbumUploadSessionResponse> createUploadSession(
            @RequestBody @Valid AlbumUploadSessionCreateRequest request
    ) {
        AlbumUploadSessionResponse response = albumUploadSessionFacade.createUploadSession(request);

        return ApiResponseTemplate.ok()
                .code("ALBM_2012")
                .message("업로드 세션이 발급되었습니다.")
                .body(response);
    }

    @Override
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponseTemplate<AlbumUploadAcceptedResponse>> completeUpload(
            @PathVariable String sessionId,
            @RequestBody @Valid AlbumUploadCompleteRequest request
    ) {
        AlbumUploadAcceptedResponse response = albumUploadSessionFacade.completeUpload(sessionId, request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponseTemplate.ok()
                        .code("ALBM_2013")
                        .message("앨범 업로드 완료 요청이 접수되었습니다.")
                        .body(response));
    }
}
