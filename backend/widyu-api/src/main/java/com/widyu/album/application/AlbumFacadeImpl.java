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
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final AlbumViewService albumViewService;
    private final MemberUtil memberUtil;

    @Override
    public AlbumUploadAcceptedResponse uploadAlbum(AlbumUploadRequest request) {
        Member currentMember = memberUtil.getCurrentMember();

        AlbumFileService.AsyncUploadPreparation prep =
                albumFileService.prepareForAsyncUpload(request.mediaFiles(), currentMember.getId());

        Long albumId = albumService.saveAlbum(
                currentMember, request.content(),
                prep.mediaUrls(), prep.thumbnailUrls(), prep.durations(),
                prep.hasVideos());

        if (prep.hasVideos()) {
            submitVideoProcessing(albumId, currentMember.getId(), prep);
        }

        return AlbumUploadAcceptedResponse.from(albumId);
    }

    private void submitVideoProcessing(Long albumId, Long memberId, AlbumFileService.AsyncUploadPreparation prep) {
        try {
            albumVideoProcessingService.processVideosAsync(albumId, memberId, prep.videoEntries());
        } catch (TaskRejectedException e) {
            // 영상 실행기가 가득 차면 PROCESSING 앨범·사진·임시 파일을 지우고 재시도를 안내한다
            log.warn("영상 처리 대기열 포화로 업로드 거절: albumId={}", albumId);
            albumFileService.discardAsyncUpload(prep);
            albumService.deleteAlbumBySystem(albumId);
            throw new BusinessException(ErrorCode.TEMPORARILY_BUSY);
        }
    }

    @Override
    public CursorPage<AlbumFeedResponse> getAlbumFeed(String cursor, String date) {
        AlbumFeedRequest request = AlbumFeedRequest.from(cursor, date);
        return albumFeedService.getAlbumFeed(request);
    }
    
    @Override
    public CursorPage<AlbumMediaResponse> getMediaFeed(String cursor) {
        AlbumFeedRequest parsed = AlbumFeedRequest.from(cursor, null);
        return albumFeedService.getMediaFeed(parsed.lastCreatedAt(), parsed.lastAlbumId());
    }

    /**
     * 상세를 읽고 그 읽기 트랜잭션이 끝난 뒤 조회 기록을 남긴다. 읽기 안에서 기록하면 요청 하나가 커넥션 두 개를 쥐어,
     * 동시 요청이 풀 크기를 넘으면 모두 두 번째 커넥션을 기다리다 실패한다(LLD-0152).
     * 해금 전 앨범과 본인 앨범은 조회로 치지 않는다.
     */
    @Override
    public AlbumDetailResponse getAlbumDetail(Long albumId) {
        AlbumDetailResponse detail = albumService.getAlbumDetail(albumId);
        if (!detail.isUnlocked() || detail.canEdit()) {
            return detail;
        }
        if (!recordFirstView(albumId)) {
            return detail;
        }
        // 첫 조회면 다시 읽어 늘어난 조회수와 조회자 목록을 응답에 담는다
        return albumService.getAlbumDetail(albumId);
    }

    private boolean recordFirstView(Long albumId) {
        try {
            return albumViewService.recordView(albumId, memberUtil.getCurrentMember());
        } catch (DataIntegrityViolationException e) {
            // 같은 회원의 첫 조회가 겹쳐 다른 요청이 먼저 기록했다(유일 키 uk_album_views_album_member)
            log.info("앨범 첫 조회 기록 중복: albumId={}", albumId);
            return true;
        }
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
