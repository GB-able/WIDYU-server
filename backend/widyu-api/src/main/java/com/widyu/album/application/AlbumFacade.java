package com.widyu.album.application;

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

/**
 * 앨범 도메인의 파사드 인터페이스
 * 여러 서비스들을 통합하여 컨트롤러에 단일 진입점을 제공
 */
public interface AlbumFacade {

    /**
     * 앨범 업로드 (비동기) - 즉시 albumId 반환, 미디어 처리는 백그라운드 수행
     */
    AlbumUploadAcceptedResponse uploadAlbum(AlbumUploadRequest request);

    /**
     * 앨범 피드 조회 (무한 스크롤)
     */
    CursorPage<AlbumFeedResponse> getAlbumFeed(String cursor, String date);
    
    /**
     * 미디어 피드 조회 (무한 스크롤)
     */
    CursorPage<AlbumMediaResponse> getMediaFeed(String cursor);
    
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
