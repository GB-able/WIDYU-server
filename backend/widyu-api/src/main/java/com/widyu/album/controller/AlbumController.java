package com.widyu.album.controller;

import com.widyu.album.controller.docs.AlbumDocs;
import com.widyu.album.application.AlbumFacade;
import com.widyu.album.dto.request.AlbumUpdateRequest;
import com.widyu.album.dto.request.AlbumUploadRequest;
import com.widyu.album.dto.response.AlbumDetailResponse;
import com.widyu.album.dto.response.AlbumFeedResponse;
import com.widyu.album.dto.response.AlbumUnlockResponse;
import com.widyu.album.dto.response.AlbumUploadAcceptedResponse;
import com.widyu.album.dto.response.AlbumUploadResponse;
import com.widyu.album.dto.response.LikedAlbumsResponse;
import com.widyu.album.dto.response.AlbumMediaResponse;
import com.widyu.global.dto.CursorPage;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/albums")
public class AlbumController implements AlbumDocs {

    private final AlbumFacade albumFacade;

    @Override
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseTemplate<AlbumUploadAcceptedResponse>> uploadAlbum(
            @ModelAttribute @Valid AlbumUploadRequest request
    ) {
        AlbumUploadAcceptedResponse response = albumFacade.uploadAlbum(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponseTemplate.ok()
                        .code("ALBM_2001")
                        .message("앨범 업로드 요청이 접수되었습니다.")
                        .body(response));
    }

    @Override
    @GetMapping("/feed")
    public ApiResponseTemplate<CursorPage<AlbumFeedResponse>> getAlbumFeed(
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "date", required = false) String date
    ) {
        CursorPage<AlbumFeedResponse> response = albumFacade.getAlbumFeed(cursor, date);
        
        return ApiResponseTemplate.ok()
                .code("ALBM_2010")
                .message("앨범 피드 조회 성공")
                .body(response);
    }

    @Override
    @GetMapping("/media")
    public ApiResponseTemplate<CursorPage<AlbumMediaResponse>> getMediaFeed(
            @RequestParam(value = "cursor", required = false) Long cursor
    ) {
        CursorPage<AlbumMediaResponse> response = albumFacade.getMediaFeed(cursor);
        
        return ApiResponseTemplate.ok()
                .code("ALBM_2011")
                .message("미디어 피드 조회 성공")
                .body(response);
    }


    @Override
    @PatchMapping("/{albumId}")
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

    @Override
    @PostMapping("/{albumId}/like")
    public ApiResponseTemplate<Void> likeAlbum(@PathVariable Long albumId) {
        albumFacade.likeAlbum(albumId);
        return ApiResponseTemplate.ok()
                .code("ALBM_2004")
                .message("앨범 좋아요가 완료되었습니다.")
                .build();
    }

    @Override
    @DeleteMapping("/{albumId}/like")
    public ApiResponseTemplate<Void> unlikeAlbum(@PathVariable Long albumId) {
        albumFacade.unlikeAlbum(albumId);
        return ApiResponseTemplate.ok()
                .code("ALBM_2005")
                .message("앨범 좋아요가 취소되었습니다.")
                .build();
    }

    @Override
    @GetMapping("/liked")
    public ApiResponseTemplate<LikedAlbumsResponse> getLikedAlbumIds() {
        LikedAlbumsResponse response = albumFacade.getLikedAlbumIds();
        return ApiResponseTemplate.ok()
                .code("ALBM_2006")
                .message("좋아요한 앨범 목록 조회 성공")
                .body(response);
    }

    @GetMapping("/{albumId}")
    public ApiResponseTemplate<AlbumDetailResponse> getAlbumDetail(@PathVariable Long albumId) {
        AlbumDetailResponse response = albumFacade.getAlbumDetail(albumId);
        return ApiResponseTemplate.ok()
                .code("ALBM_2007")
                .message("앨범 상세 조회 성공")
                .body(response);
    }

    @Override
    @PostMapping("/{albumId}/unlock")
    public ApiResponseTemplate<AlbumUnlockResponse> unlockAlbum(@PathVariable Long albumId) {
        AlbumUnlockResponse response = albumFacade.unlockAlbum(albumId);
        return ApiResponseTemplate.ok()
                .code("ALBM_2008")
                .message("앨범 해금이 완료되었습니다.")
                .body(response);
    }
}
