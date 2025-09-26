package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.request.AlbumFeedRequest;
import com.widyu.domain.album.dto.request.AlbumUpdateRequest;
import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumFeedResponse;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.dto.response.MediaItem;
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

    @Override
    public AlbumUploadResponse uploadAlbum(AlbumUploadRequest request) {
        return albumService.uploadAlbum(request);
    }

    @Override
    public CursorPage<AlbumFeedResponse> getAlbumFeed(Long lastAlbumId) {
        AlbumFeedRequest request = AlbumFeedRequest.from(lastAlbumId);
        return albumFeedService.getAlbumFeed(request);
    }
    
    @Override
    public CursorPage<MediaItem> getMediaFeed(Long lastPostId) {
        return albumFeedService.getMediaFeed(lastPostId);
    }

    @Override
    public AlbumUploadResponse updateAlbum(Long albumId, AlbumUpdateRequest request) {
        return albumService.updateAlbum(albumId, request);
    }
    
    @Override
    public void deleteAlbum(Long albumId) {
        albumService.deleteAlbum(albumId);
    }
}
