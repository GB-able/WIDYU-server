package com.widyu.album.application;

import com.widyu.album.dto.request.AlbumFeedRequest;
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
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 앨범 도메인 파사드 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumFacadeImpl implements AlbumFacade {

    private final AlbumFeedService albumFeedService;
    private final AlbumService albumService;
    private final AlbumLikeService albumLikeService;
    private final AlbumUnlockService albumUnlockService;
    private final AlbumFileService albumFileService;
    private final AlbumMediaPolicy mediaPolicy;
    private final AlbumVideoProcessingService albumVideoProcessingService;
    private final MemberUtil memberUtil;

    @Override
    public AlbumUploadAcceptedResponse uploadAlbum(AlbumUploadRequest request) {
        Member currentMember = memberUtil.getCurrentMember();
        mediaPolicy.validate(request.mediaFiles());

        List<String> mediaUrls = new ArrayList<>();
        List<String> thumbnailUrls = new ArrayList<>();
        List<Integer> durations = new ArrayList<>();
        List<AlbumVideoProcessingService.VideoEntry> videoEntries = new ArrayList<>();

        for (int i = 0; i < request.mediaFiles().size(); i++) {
            MultipartFile file = request.mediaFiles().get(i);
            String contentType = file.getContentType();

            if (contentType == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }

            if (contentType.startsWith("image/")) {
                String url = albumFileService.uploadAlbumPhoto(file, currentMember.getId());
                mediaUrls.add(url);
                thumbnailUrls.add(null);
                durations.add(null);
            } else if (contentType.startsWith("video/")) {
                try {
                    File tempFile = albumFileService.toTempFile(file);
                    videoEntries.add(new AlbumVideoProcessingService.VideoEntry(
                            i, tempFile, file.getOriginalFilename(), contentType));
                    mediaUrls.add("");
                    thumbnailUrls.add(null);
                    durations.add(null);
                } catch (IOException e) {
                    throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
                }
            } else {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }
        }

        boolean hasVideos = !videoEntries.isEmpty();
        Long albumId = albumService.saveAlbum(
                currentMember, request.content(), mediaUrls, thumbnailUrls, durations, hasVideos);

        if (hasVideos) {
            albumVideoProcessingService.processVideosAsync(albumId, currentMember.getId(), videoEntries);
        }

        return new AlbumUploadAcceptedResponse(albumId);
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
