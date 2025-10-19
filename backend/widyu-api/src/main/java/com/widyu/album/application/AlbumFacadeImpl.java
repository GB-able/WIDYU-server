package com.widyu.album.application;

import com.widyu.album.dto.request.AlbumFeedRequest;
import com.widyu.album.dto.request.AlbumUpdateRequest;
import com.widyu.album.dto.request.AlbumUploadRequest;
import com.widyu.album.dto.response.AlbumDetailResponse;
import com.widyu.album.dto.response.AlbumFeedResponse;
import com.widyu.album.dto.response.AlbumUnlockResponse;
import com.widyu.album.dto.response.AlbumUploadResponse;
import com.widyu.album.dto.response.LikedAlbumsResponse;
import com.widyu.album.dto.response.AlbumMediaResponse;
import com.widyu.global.dto.CursorPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 앨범 도메인 파사드 구현체
 * 
 * 여러 서비스들을 조합하여 앨범 도메인의 모든 기능을 제공하는 파사드 패턴 구현
 * - AlbumUploadService: 업로드 처리
 * - AlbumFeedService: 피드 조회 처리  
 * - AlbumService: 수정/삭제 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumFacadeImpl implements AlbumFacade {
    
    private final AlbumFeedService albumFeedService;
    private final AlbumService albumService;
    private final AlbumLikeService albumLikeService;
    private final AlbumUnlockService albumUnlockService;

    @Override
    public AlbumUploadResponse uploadAlbum(AlbumUploadRequest request) {
        return albumService.uploadAlbum(request);
    }

    @Override
    public CursorPage<AlbumFeedResponse> getAlbumFeed(Long lastAlbumId, String date) {
        AlbumFeedRequest request = AlbumFeedRequest.from(lastAlbumId, date);
        return albumFeedService.getAlbumFeed(request);
    }
    
    @Override
    public CursorPage<AlbumMediaResponse> getMediaFeed(Long lastPostId) {
        return albumFeedService.getMediaFeed(lastPostId);
    }

    @Override
    public AlbumDetailResponse getAlbumDetail(Long albumId) {
        return albumService.getAlbumDetail(albumId);
    }

    @Override
    public AlbumUploadResponse updateAlbum(Long albumId, AlbumUpdateRequest request) {
        return albumService.updateAlbum(albumId, request);
    }
    
    @Override
    public void deleteAlbum(Long albumId) {
        albumService.deleteAlbum(albumId);
    }
    
    @Override
    public void likeAlbum(Long albumId) {
        albumLikeService.likeAlbum(albumId);
    }
    
    @Override
    public void unlikeAlbum(Long albumId) {
        albumLikeService.unlikeAlbum(albumId);
    }
    
    @Override
    public LikedAlbumsResponse getLikedAlbumIds() {
        return albumLikeService.getLikedAlbumIds();
    }
    
    @Override
    public AlbumUnlockResponse unlockAlbum(Long albumId) {
        return albumUnlockService.unlockAlbum(albumId);
    }
}
