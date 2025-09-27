package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.request.AlbumUpdateRequest;
import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumDetailResponse;
import com.widyu.domain.album.dto.response.AlbumFeedResponse;
import com.widyu.domain.album.dto.response.AlbumUnlockResponse;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.dto.response.LikedAlbumsResponse;
import com.widyu.domain.album.dto.response.MediaItem;
import com.widyu.global.dto.CursorPage;

/**
 * 앨범 도메인의 파사드 인터페이스
 * 여러 서비스들을 통합하여 컨트롤러에 단일 진입점을 제공
 */
public interface AlbumFacade {

    /**
     * 앨범 업로드
     */
    AlbumUploadResponse uploadAlbum(AlbumUploadRequest request);

    /**
     * 앨범 피드 조회 (무한 스크롤)
     */
    CursorPage<AlbumFeedResponse> getAlbumFeed(Long lastAlbumId);
    
    /**
     * 미디어 피드 조회 (무한 스크롤)
     */
    CursorPage<MediaItem> getMediaFeed(Long lastPostId);
    
    /**
     * 앨범 상세 조회
     */
    AlbumDetailResponse getAlbumDetail(Long albumId);
    

    /**
     * 앨범 수정
     */
    AlbumUploadResponse updateAlbum(Long albumId, AlbumUpdateRequest request);
    
    /**
     * 앨범 삭제
     */
    void deleteAlbum(Long albumId);
    
    /**
     * 앨범 좋아요
     */
    void likeAlbum(Long albumId);
    
    /**
     * 앨범 좋아요 취소
     */
    void unlikeAlbum(Long albumId);
    
    /**
     * 좋아요한 앨범 목록 조회
     */
    LikedAlbumsResponse getLikedAlbumIds();
    
    /**
     * 앨범 해금
     */
    AlbumUnlockResponse unlockAlbum(Long albumId);
}
