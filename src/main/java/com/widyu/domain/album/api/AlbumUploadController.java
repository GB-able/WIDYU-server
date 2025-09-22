package com.widyu.domain.album.api;

import com.widyu.domain.album.api.docs.AlbumUploadDocs;
import com.widyu.domain.album.application.AlbumUploadService;
import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/albums")
public class AlbumUploadController implements AlbumUploadDocs {

    private final AlbumUploadService albumUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseTemplate<AlbumUploadResponse> uploadAlbum(
            @ModelAttribute @Valid AlbumUploadRequest request
    ) {

        AlbumUploadResponse response = albumUploadService.uploadAlbum(request);
        
        return ApiResponseTemplate.ok()
                .code("ALBM_2001")
                .message("앨범 업로드가 완료되었습니다.")
                .body(response);
    }
}