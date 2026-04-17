package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumLikeService 단위 테스트")
class AlbumLikeServiceTest {

    @Mock private AlbumLikeRepository albumLikeRepository;
    @Mock private AlbumRepository albumRepository;
    @Mock private MemberUtil memberUtil;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlbumLikeService albumLikeService;

    @Test
    @DisplayName("앨범에 좋아요를 누르면 좋아요가 저장되고 좋아요 수가 증가한다")
    void 앨범_좋아요_시_저장되고_좋아요수_증가() {
        // given
        Member currentMember = mock(Member.class);
        given(currentMember.getId()).willReturn(1L);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);

        Member albumOwner = mock(Member.class);
        given(albumOwner.getId()).willReturn(2L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(albumOwner);
        given(albumRepository.findByIdAndStatus(1L, Status.ACTIVE)).willReturn(Optional.of(album));
        given(albumLikeRepository.existsByAlbumAndMember(album, currentMember)).willReturn(false);

        // when
        albumLikeService.likeAlbum(1L);

        // then
        verify(albumLikeRepository).save(any(AlbumLike.class));
        verify(album).incrementLikeCount();
        verify(eventPublisher).publishEvent(any(AlbumLikedEvent.class));
    }

    @Test
    @DisplayName("이미 좋아요한 앨범에 다시 좋아요를 누르면 BusinessException을 던진다")
    void 이미_좋아요한_앨범에_좋아요_시_예외가_발생한다() {
        // given
        Member currentMember = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);

        Album album = mock(Album.class);
        given(albumRepository.findByIdAndStatus(1L, Status.ACTIVE)).willReturn(Optional.of(album));
        given(albumLikeRepository.existsByAlbumAndMember(album, currentMember)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> albumLikeService.likeAlbum(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_ALREADY_LIKED);
    }

    @Test
    @DisplayName("존재하지 않는 앨범에 좋아요 시 BusinessException을 던진다")
    void 존재하지_않는_앨범에_좋아요_시_예외가_발생한다() {
        // given
        Member currentMember = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(albumRepository.findByIdAndStatus(999L, Status.ACTIVE)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumLikeService.likeAlbum(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_NOT_FOUND);
    }

    @Test
    @DisplayName("좋아요 취소 시 좋아요가 삭제되고 좋아요 수가 감소한다")
    void 좋아요_취소_시_삭제되고_좋아요수_감소() {
        // given
        Member currentMember = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);

        Album album = mock(Album.class);
        given(albumRepository.findByIdAndStatus(1L, Status.ACTIVE)).willReturn(Optional.of(album));

        AlbumLike albumLike = mock(AlbumLike.class);
        given(albumLikeRepository.findByAlbumAndMember(album, currentMember)).willReturn(Optional.of(albumLike));

        // when
        albumLikeService.unlikeAlbum(1L);

        // then
        verify(albumLikeRepository).delete(albumLike);
        verify(album).decrementLikeCount();
    }

    @Test
    @DisplayName("좋아요하지 않은 앨범을 취소하려 하면 BusinessException을 던진다")
    void 좋아요하지_않은_앨범_취소_시_예외가_발생한다() {
        // given
        Member currentMember = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);

        Album album = mock(Album.class);
        given(albumRepository.findByIdAndStatus(1L, Status.ACTIVE)).willReturn(Optional.of(album));
        given(albumLikeRepository.findByAlbumAndMember(album, currentMember)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumLikeService.unlikeAlbum(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_NOT_LIKED);
    }

    @Test
    @DisplayName("좋아요한 앨범 ID 목록 조회 시 현재 회원의 좋아요 목록을 반환한다")
    void 좋아요한_앨범_ID_목록_조회_시_현재_회원의_목록을_반환한다() {
        // given
        Member currentMember = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(albumLikeRepository.findAlbumIdsByMember(currentMember)).willReturn(List.of(1L, 2L, 3L));

        // when
        LikedAlbumsResponse response = albumLikeService.getLikedAlbumIds();

        // then
        assertThat(response.albumIds()).containsExactly(1L, 2L, 3L);
    }
}
