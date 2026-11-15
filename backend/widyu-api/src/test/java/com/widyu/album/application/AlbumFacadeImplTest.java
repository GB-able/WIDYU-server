package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.widyu.album.dto.request.AlbumUploadRequest;
import com.widyu.album.dto.response.AlbumDetailResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumFacadeImpl 단위 테스트")
class AlbumFacadeImplTest {

    @Mock private AlbumFeedService albumFeedService;
    @Mock private AlbumService albumService;
    @Mock private AlbumLikeService albumLikeService;
    @Mock private AlbumUnlockService albumUnlockService;
    @Mock private AlbumFileService albumFileService;
    @Mock private AlbumMediaPolicy mediaPolicy;
    @Mock private AlbumVideoProcessingService albumVideoProcessingService;
    @Mock private AlbumViewService albumViewService;
    @Mock private MemberUtil memberUtil;

    @InjectMocks
    private AlbumFacadeImpl albumFacade;

    @Test
    @DisplayName("영상 실행기가 가득 차 제출이 거절되면 앨범과 업로드 준비물을 지우고 재시도 가능한 503 예외를 던진다")
    void 영상_실행기가_가득_차면_앨범과_준비물을_지우고_재시도_가능한_예외가_발생한다() {
        // given
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01011112222");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberUtil.getCurrentMember()).willReturn(member);
        AlbumUploadRequest request = new AlbumUploadRequest("가족 여행", List.of(
                new MockMultipartFile("mediaFiles", "video.mp4", "video/mp4", new byte[]{1})));
        AlbumFileService.AsyncUploadPreparation prep = new AlbumFileService.AsyncUploadPreparation(
                List.of(""), Arrays.asList((String) null), Arrays.asList((Integer) null),
                List.of(new AlbumVideoProcessingService.VideoEntry(0, new File("source.mp4"), "video.mp4", "video/mp4")));
        given(albumFileService.prepareForAsyncUpload(request.mediaFiles(), 1L)).willReturn(prep);
        given(albumService.saveAlbum(eq(member), any(), anyList(), anyList(), anyList(), eq(true))).willReturn(10L);
        willThrow(new TaskRejectedException("album-video saturated"))
                .given(albumVideoProcessingService).processVideosAsync(10L, 1L, prep.videoEntries());

        // when & then
        assertThatThrownBy(() -> albumFacade.uploadAlbum(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TEMPORARILY_BUSY);
        then(albumService).should().deleteAlbumBySystem(10L);
        then(albumFileService).should().discardAsyncUpload(prep);
    }

    @Test
    @DisplayName("해금한 타인 앨범을 처음 열면 상세를 읽은 뒤 조회 기록을 남기고 다시 읽은 상세를 반환한다")
    void 해금한_타인_앨범을_처음_열면_읽은_뒤_기록하고_다시_읽는다() {
        // given
        Member viewer = viewer();
        AlbumDetailResponse beforeView = unlockedDetail(false);
        AlbumDetailResponse afterView = mock(AlbumDetailResponse.class);
        given(albumService.getAlbumDetail(10L)).willReturn(beforeView, afterView);
        given(memberUtil.getCurrentMember()).willReturn(viewer);
        given(albumViewService.recordView(10L, viewer)).willReturn(true);

        // when
        AlbumDetailResponse response = albumFacade.getAlbumDetail(10L);

        // then
        assertThat(response).isSameAs(afterView);
        InOrder inOrder = inOrder(albumService, albumViewService);
        inOrder.verify(albumService).getAlbumDetail(10L);
        inOrder.verify(albumViewService).recordView(10L, viewer);
        inOrder.verify(albumService).getAlbumDetail(10L);
    }

    @Test
    @DisplayName("이미 본 앨범을 다시 열면 상세를 한 번만 읽고 그대로 반환한다")
    void 이미_본_앨범을_다시_열면_한_번만_읽는다() {
        // given
        Member viewer = viewer();
        AlbumDetailResponse detail = unlockedDetail(false);
        given(albumService.getAlbumDetail(10L)).willReturn(detail);
        given(memberUtil.getCurrentMember()).willReturn(viewer);
        given(albumViewService.recordView(10L, viewer)).willReturn(false);

        // when
        AlbumDetailResponse response = albumFacade.getAlbumDetail(10L);

        // then
        assertThat(response).isSameAs(detail);
        then(albumService).should().getAlbumDetail(10L);
    }

    @Test
    @DisplayName("같은 회원의 첫 조회가 겹쳐 조회 기록이 유일 키에 걸리면 예외 없이 다시 읽은 상세를 반환한다")
    void 첫_조회가_겹쳐_유일_키에_걸려도_상세를_반환한다() {
        // given
        Member viewer = viewer();
        AlbumDetailResponse beforeView = unlockedDetail(false);
        AlbumDetailResponse afterView = mock(AlbumDetailResponse.class);
        given(albumService.getAlbumDetail(10L)).willReturn(beforeView, afterView);
        given(memberUtil.getCurrentMember()).willReturn(viewer);
        willThrow(new DataIntegrityViolationException("uk_album_views_album_member"))
                .given(albumViewService).recordView(10L, viewer);

        // when
        AlbumDetailResponse response = albumFacade.getAlbumDetail(10L);

        // then
        assertThat(response).isSameAs(afterView);
    }

    @Test
    @DisplayName("해금하지 않은 앨범을 열면 조회 기록을 남기지 않는다")
    void 해금하지_않은_앨범은_조회_기록을_남기지_않는다() {
        // given
        AlbumDetailResponse detail = mock(AlbumDetailResponse.class);
        given(detail.isUnlocked()).willReturn(false);
        given(albumService.getAlbumDetail(10L)).willReturn(detail);

        // when
        AlbumDetailResponse response = albumFacade.getAlbumDetail(10L);

        // then
        assertThat(response).isSameAs(detail);
        then(albumViewService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("본인 앨범을 열면 조회 기록을 남기지 않는다")
    void 본인_앨범은_조회_기록을_남기지_않는다() {
        // given
        AlbumDetailResponse detail = unlockedDetail(true);
        given(albumService.getAlbumDetail(10L)).willReturn(detail);

        // when
        AlbumDetailResponse response = albumFacade.getAlbumDetail(10L);

        // then
        assertThat(response).isSameAs(detail);
        then(albumViewService).shouldHaveNoInteractions();
    }

    private Member viewer() {
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01011112222");
        ReflectionTestUtils.setField(member, "id", 2L);
        return member;
    }

    private AlbumDetailResponse unlockedDetail(boolean canEdit) {
        AlbumDetailResponse detail = mock(AlbumDetailResponse.class);
        given(detail.isUnlocked()).willReturn(true);
        given(detail.canEdit()).willReturn(canEdit);
        return detail;
    }
}
