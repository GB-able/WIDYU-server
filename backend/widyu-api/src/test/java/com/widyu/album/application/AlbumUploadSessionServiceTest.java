package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.album.AlbumUploadSession;
import com.widyu.album.AlbumUploadSessionFile;
import com.widyu.album.AlbumUploadSessionStatus;
import com.widyu.album.repository.AlbumUploadSessionRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumUploadSessionService 단위 테스트")
class AlbumUploadSessionServiceTest {

    @Mock
    private AlbumUploadSessionRepository albumUploadSessionRepository;

    @InjectMocks
    private AlbumUploadSessionService albumUploadSessionService;

    @Test
    @DisplayName("대기 세션을 저장하면 WAITING 상태와 6시간 TTL로 저장한다")
    void 대기_세션을_저장하면_WAITING_상태로_저장한다() {
        // given
        List<AlbumUploadSessionFile> files = List.of(
                AlbumUploadSessionFile.photo(0, "photo.jpg", "image/jpeg", 1024L, "albums/staging/1/sid/0_abc.jpg")
        );

        // when
        albumUploadSessionService.saveWaitingSession("session-1", 1L, files);

        // then
        ArgumentCaptor<AlbumUploadSession> captor = ArgumentCaptor.forClass(AlbumUploadSession.class);
        then(albumUploadSessionRepository).should().save(captor.capture());
        AlbumUploadSession saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlbumUploadSessionStatus.WAITING);
        assertThat(saved.getMemberId()).isEqualTo(1L);
        assertThat(saved.getTtl()).isEqualTo(AlbumUploadSession.WAITING_TTL_SECONDS);
    }

    @Test
    @DisplayName("세션이 없으면 ALBUM_UPLOAD_SESSION_NOT_FOUND 예외를 던진다")
    void 세션이_없으면_예외가_발생한다() {
        // given
        given(albumUploadSessionRepository.findById("unknown")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumUploadSessionService.getSession("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_UPLOAD_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("완료 처리하면 COMPLETED 상태와 albumId, 10분 TTL로 저장한다")
    void 완료_처리하면_COMPLETED_상태로_저장한다() {
        // given
        AlbumUploadSession session = AlbumUploadSession.createWaiting("session-1", 1L, List.of());

        // when
        albumUploadSessionService.markCompleted(session, 100L);

        // then
        ArgumentCaptor<AlbumUploadSession> captor = ArgumentCaptor.forClass(AlbumUploadSession.class);
        then(albumUploadSessionRepository).should().save(captor.capture());
        AlbumUploadSession saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlbumUploadSessionStatus.COMPLETED);
        assertThat(saved.getAlbumId()).isEqualTo(100L);
        assertThat(saved.getTtl()).isEqualTo(AlbumUploadSession.COMPLETED_TTL_SECONDS);
    }
}
