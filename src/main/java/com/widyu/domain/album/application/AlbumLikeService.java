package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.response.LikedAlbumsResponse;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumLike;
import com.widyu.domain.album.repository.AlbumLikeRepository;
import com.widyu.domain.album.repository.AlbumRepository;
import com.widyu.domain.fcm.event.album.dto.AlbumLikedEvent;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
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

    @Transactional
    public void likeAlbum(Long albumId) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatus(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

        if (albumLikeRepository.existsByAlbumAndMember(album, currentMember)) {
            throw new BusinessException(ErrorCode.ALBUM_ALREADY_LIKED);
        }

        AlbumLike albumLike = AlbumLike.createLike(album, currentMember);
        albumLikeRepository.save(albumLike);

        // 앨범 좋아요 수 증가
        album.incrementLikeCount();

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

        AlbumLike albumLike = albumLikeRepository.findByAlbumAndMember(album, currentMember)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_LIKED));

        albumLikeRepository.delete(albumLike);
        
        // 앨범 좋아요 수 감소
        album.decrementLikeCount();
    }

    @Transactional(readOnly = true)
    public LikedAlbumsResponse getLikedAlbumIds() {
        Member currentMember = memberUtil.getCurrentMember();
        List<Long> albumIds = albumLikeRepository.findAlbumIdsByMember(currentMember);
        return LikedAlbumsResponse.from(albumIds);
    }
}
