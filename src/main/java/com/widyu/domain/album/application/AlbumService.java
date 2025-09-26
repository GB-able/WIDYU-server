package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.request.AlbumUpdateRequest;
import com.widyu.domain.album.dto.request.AlbumUploadRequest;
import com.widyu.domain.album.dto.response.AlbumDetailResponse;
import com.widyu.domain.album.dto.response.AlbumUploadResponse;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumComment;
import com.widyu.domain.album.repository.AlbumCommentRepository;
import com.widyu.domain.album.repository.AlbumRepository;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final AlbumCommentRepository albumCommentRepository;
    private final AlbumFileService albumFileService;
    private final AlbumViewService albumViewService;
    private final AlbumPermissionService albumPermissionService;
    private final MemberUtil memberUtil;
    private final AlbumMediaPolicy mediaPolicy;

    @Transactional
    public AlbumUploadResponse uploadAlbum(AlbumUploadRequest request) {
        // 1) 사용자
        Member currentMember = memberUtil.getCurrentMember();

        // 2) 정책 검증(개수/타입/용량)
        mediaPolicy.validate(request.mediaFiles());

        // 3) 업로드 (이미지/비디오+썸네일/길이)
        AlbumFileService.UploadResult uploadResult =
                albumFileService.uploadMediaFilesWithThumbnails(request.mediaFiles(), currentMember.getId());

        // 4) 앨범 저장
        Album album = Album.createAlbumWithMetadata(
                currentMember,
                request.content(),
                uploadResult.mediaUrls(),
                uploadResult.thumbnailUrls(),
                uploadResult.durations()
        );
        Album saved = albumRepository.save(album);

        return AlbumUploadResponse.from(saved);
    }

    @Transactional
    public AlbumUploadResponse updateAlbum(Long albumId, AlbumUpdateRequest request) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatus(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "앨범을 찾을 수 없습니다."));

        if (!album.getMember().getId().equals(currentMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 앨범만 수정할 수 있습니다.");
        }

        album.updateContent(request.content());

        log.info("앨범 수정 완료: albumId={}, memberId={}", albumId, currentMember.getId());
        return AlbumUploadResponse.from(album);
    }

    @Transactional
    public void deleteAlbum(Long albumId) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatus(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "앨범을 찾을 수 없습니다."));

        if (!album.getMember().getId().equals(currentMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인의 앨범만 삭제할 수 있습니다.");
        }

        album.delete();

        log.info("앨범 삭제 완료: albumId={}, memberId={}", albumId, currentMember.getId());
    }

    @Transactional(readOnly = true)
    public AlbumDetailResponse getAlbumDetail(Long albumId) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatusWithCollections(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

        // 권한 검사
        albumPermissionService.checkViewPermission(album, currentMember);

        // 조회 기록 추가 (본인 앨범이 아닌 경우에만)
        if (!album.getMember().getId().equals(currentMember.getId())) {
            albumViewService.recordView(album, currentMember);
        }

        // 댓글 목록 조회 (최상위 댓글만, 대댓글은 응답에서 포함)
        List<AlbumComment> comments = albumCommentRepository.findTopLevelCommentsByAlbumAndStatus(album, Status.ACTIVE);

        // 조회자 목록 조회 (최근 조회자 최대 3명)
        List<Member> viewers = albumViewService.getRecentViewers(album, 3);

        log.info("앨범 상세 조회 완료: albumId={}, memberId={}", albumId, currentMember.getId());
        return AlbumDetailResponse.from(album, currentMember.getId(), viewers, comments);
    }
}
