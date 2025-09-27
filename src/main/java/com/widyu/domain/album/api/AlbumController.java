package com.widyu.domain.album.api;

import com.widyu.domain.album.api.docs.AlbumDocs;
import com.widyu.domain.album.application.AlbumFacade;
import com.widyu.domain.album.dto.request.AlbumUpdateRequest;
import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumDetailResponse;
import com.widyu.domain.album.dto.response.AlbumFeedResponse;
import com.widyu.domain.album.dto.response.AlbumUnlockResponse;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.dto.response.LikedAlbumsResponse;
import com.widyu.domain.album.dto.response.MediaItem;
import com.widyu.global.dto.CursorPage;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    @Operation(summary = "앨범 해금", description = "포인트를 사용하여 다른 사용자의 앨범을 해금합니다. 해금 가격은 50포인트로 고정되어 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "앨범 해금 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (본인 앨범 해금 시도, 이미 해금된 앨범, 포인트 부족)"),
            @ApiResponse(responseCode = "404", description = "앨범을 찾을 수 없음")
    })
    @PostMapping("/{albumId}/unlock")
    public ApiResponseTemplate<AlbumUnlockResponse> unlockAlbum(
            @Parameter(description = "해금할 앨범 ID", required = true) @PathVariable Long albumId) {
        AlbumUnlockResponse response = albumFacade.unlockAlbum(albumId);
        return ApiResponseTemplate.ok()
                .code("ALBM_2008")
                .message("앨범 해금이 완료되었습니다.")
                .body(response);
    }
}
