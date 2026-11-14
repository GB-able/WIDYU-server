package com.widyu.album.application;

import com.widyu.album.Album;
import com.widyu.album.AlbumLike;
import com.widyu.album.dto.response.LikedAlbumsResponse;
import com.widyu.album.repository.AlbumLikeRepository;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.fcm.event.album.dto.AlbumLikedEvent;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumLikeService {

    private final AlbumLikeRepository albumLikeRepository;
    private final AlbumRepository albumRepository;
    private final MemberUtil memberUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final AlbumPermissionService albumPermissionService;

    @Transactional
    public void likeAlbum(Long albumId) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatus(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
        albumPermissionService.checkFamilyAccess(album, currentMember);
        albumPermissionService.checkUnlocked(album, currentMember);

        if (albumLikeRepository.existsByAlbumAndMember(album, currentMember)) {
            throw new BusinessException(ErrorCode.ALBUM_ALREADY_LIKED);
        }

        // 카운터를 먼저 올려 앨범 행 배타 잠금을 잡는다. 좋아요 INSERT의 외래키 검사(공유 잠금)를 먼저 하면
        // 같은 앨범에 동시에 좋아요한 요청끼리 서로의 공유 잠금을 기다리며 교착한다.
        albumRepository.incrementLikeCount(albumId);
        albumLikeRepository.save(AlbumLike.createLike(album, currentMember));

        // 좋아요 알림 이벤트 발행
        eventPublisher.publishEvent(new AlbumLikedEvent(
                albumId,
                currentMember.getId(),
                album.getMember().getId()
        ));
    }

    @Transactional
    public void unlikeAlbum(Long albumId) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatus(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
        albumPermissionService.checkFamilyAccess(album, currentMember);

        AlbumLike albumLike = albumLikeRepository.findByAlbumAndMember(album, currentMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_LIKED));

        albumLikeRepository.delete(albumLike);
        albumRepository.decrementLikeCount(albumId);
    }

    @Transactional(readOnly = true)
    public LikedAlbumsResponse getLikedAlbumIds() {
        Member currentMember = memberUtil.getCurrentMember();
        List<Long> albumIds = albumLikeRepository.findAlbumIdsByMember(currentMember);
        return LikedAlbumsResponse.from(albumIds);
    }
}
