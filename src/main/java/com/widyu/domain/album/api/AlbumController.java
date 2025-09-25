package com.widyu.domain.album.api;

import com.widyu.domain.album.api.docs.AlbumDocs;
import com.widyu.domain.album.application.AlbumFacade;
import com.widyu.domain.album.dto.request.AlbumUpdateRequest;
import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumFeedResponse;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.dto.response.MediaItem;
import com.widyu.global.dto.CursorPage;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "앨범", description = "앨범 관리 API - 업로드, 조회, 수정, 삭제")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/albums")
public class AlbumController implements AlbumDocs {

    private final AlbumFacade albumFacade;

    @Override
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseTemplate<AlbumUploadResponse> uploadAlbum(
            @ModelAttribute @Valid AlbumUploadRequest request
    ) {
        AlbumUploadResponse response = albumFacade.uploadAlbum(request);
        
        return ApiResponseTemplate.ok()
                .code("ALBM_2001")
                .message("앨범 업로드가 완료되었습니다.")
                .body(response);
    }

    @Override
    @GetMapping("/feed")
    public ApiResponseTemplate<CursorPage<AlbumFeedResponse>> getAlbumFeed(
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        CursorPage<AlbumFeedResponse> response = albumFacade.getAlbumFeed(cursor);
        
        return ApiResponseTemplate.ok()
                .code("ALBM_2010")
                .message("앨범 피드 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/media")
    public ApiResponseTemplate<CursorPage<MediaItem>> getMediaFeed(
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        CursorPage<MediaItem> response = albumFacade.getMediaFeed(cursor);
        
        return ApiResponseTemplate.ok()
                .code("ALBM_2011")
                .message("미디어 피드 조회 성공")
                .body(response);
    }


    @Override
    @PutMapping("/{albumId}")
    public ApiResponseTemplate<AlbumUploadResponse> updateAlbum(
            @PathVariable Long albumId,
            @Valid @RequestBody AlbumUpdateRequest request
    ) {
        AlbumUploadResponse response = albumFacade.updateAlbum(albumId, request);
        return ApiResponseTemplate.ok()
                .code("ALBM_2002")
                .message("앨범이 수정되었습니다.")
                .body(response);
    }

    @Override
    @DeleteMapping("/{albumId}")
    public ApiResponseTemplate<Void> deleteAlbum(
            @PathVariable Long albumId
    ) {
        albumFacade.deleteAlbum(albumId);
        return ApiResponseTemplate.ok()
                .code("ALBM_2003")
                .message("앨범이 삭제되었습니다.")
                .build();
    }
}
