package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.infrastructure.s3.S3DirectUploadService;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumVideoProcessingService 단위 테스트")
class AlbumVideoProcessingServiceTest {

    @Mock private AlbumRepository albumRepository;
    @Mock private AlbumFileService albumFileService;
    @Mock private S3DirectUploadService s3DirectUploadService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlbumVideoProcessingService albumVideoProcessingService;

    @Test
    @DisplayName("영상 비동기 처리가 성공하면 앨범을 ACTIVE로 전환하고 이벤트를 발행한다")
    void 영상_비동기_처리_성공_시_ACTIVE로_전환하고_이벤트를_발행한다() throws IOException {
        // given
        Long albumId = 10L;
        Long memberId = 1L;
        Album album = processingAlbum(2);
        List<AlbumVideoProcessingService.VideoEntry> entries = List.of(
                videoEntry(0),
                videoEntry(1)
        );

        given(albumFileService.uploadAlbumVideoWithThumbnail(any(MultipartFile.class), eq(memberId)))
                .willReturn(new AlbumFileService.VideoUploadResult("https://cdn/video-1.mp4", "https://cdn/thumb-1.jpg", 10))
                .willReturn(new AlbumFileService.VideoUploadResult("https://cdn/video-2.mp4", "https://cdn/thumb-2.jpg", 20));
        given(albumRepository.findById(albumId)).willReturn(Optional.of(album));

        // when
        albumVideoProcessingService.processVideosAsync(albumId, memberId, entries);

        // then
        assertThat(album.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(album.getMediaUrls()).containsExactly("https://cdn/video-1.mp4", "https://cdn/video-2.mp4");
        assertThat(album.getThumbnailUrls()).containsExactly("https://cdn/thumb-1.jpg", "https://cdn/thumb-2.jpg");
        assertThat(album.getDurations()).containsExactly(10, 20);
        assertThat(entries).allMatch(entry -> !entry.tempFile().exists());
        then(eventPublisher).should().publishEvent(new AlbumCreatedEvent(albumId, memberId));
        then(albumFileService).should(never()).cleanupUploadedFiles(any());
    }

    @Test
    @DisplayName("영상 비동기 처리가 실패하면 앨범을 DELETED로 전환하고 업로드 파일을 정리한다")
    void 영상_비동기_처리_실패_시_DELETED로_전환하고_업로드_파일을_정리한다() throws IOException {
        // given
        Long albumId = 10L;
        Long memberId = 1L;
        Album album = processingAlbum(2);
        List<AlbumVideoProcessingService.VideoEntry> entries = List.of(
                videoEntry(0),
                videoEntry(1)
        );

        given(albumFileService.uploadAlbumVideoWithThumbnail(any(MultipartFile.class), eq(memberId)))
                .willReturn(new AlbumFileService.VideoUploadResult("https://cdn/video-1.mp4", "https://cdn/thumb-1.jpg", 10))
                .willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_FAILED));
        given(albumRepository.findById(albumId)).willReturn(Optional.of(album));

        // when
        albumVideoProcessingService.processVideosAsync(albumId, memberId, entries);

        // then
        assertThat(album.getStatus()).isEqualTo(Status.DELETED);
        assertThat(entries).allMatch(entry -> !entry.tempFile().exists());
        then(albumFileService).should().cleanupUploadedFiles(List.of("https://cdn/video-1.mp4", "https://cdn/thumb-1.jpg"));
        then(eventPublisher).should(never()).publishEvent(any(AlbumCreatedEvent.class));
    }

    @Test
    @DisplayName("스테이징 영상 처리가 성공하면 앨범을 ACTIVE로 전환하고 스테이징 원본을 삭제한다")
    void 스테이징_영상_처리_성공_시_ACTIVE로_전환하고_스테이징_원본을_삭제한다() throws IOException {
        // given
        Long albumId = 10L;
        Long memberId = 1L;
        Album album = processingAlbum(1);
        File tempFile = Files.createTempFile("staged_video_", ".mp4").toFile();
        List<AlbumVideoProcessingService.StagedVideoEntry> stagedEntries = List.of(
                new AlbumVideoProcessingService.StagedVideoEntry(0, "albums/staging/1/sid/0_abc.mp4", "video.mp4", "video/mp4")
        );

        given(s3DirectUploadService.downloadToTempFile("albums/staging/1/sid/0_abc.mp4")).willReturn(tempFile);
        given(albumFileService.uploadAlbumVideoWithThumbnail(any(MultipartFile.class), eq(memberId)))
                .willReturn(new AlbumFileService.VideoUploadResult("https://cdn/video-1.mp4", "https://cdn/thumb-1.jpg", 10));
        given(albumRepository.findById(albumId)).willReturn(Optional.of(album));

        // when
        albumVideoProcessingService.processStagedVideosAsync(albumId, memberId, stagedEntries);

        // then
        assertThat(album.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(album.getMediaUrls()).containsExactly("https://cdn/video-1.mp4");
        assertThat(tempFile).doesNotExist();
        then(s3DirectUploadService).should().deleteObject("albums/staging/1/sid/0_abc.mp4");
        then(eventPublisher).should().publishEvent(new AlbumCreatedEvent(albumId, memberId));
    }

    @Test
    @DisplayName("스테이징 영상 다운로드가 실패하면 앨범을 DELETED로 전환하고 스테이징 원본을 삭제한다")
    void 스테이징_영상_다운로드_실패_시_DELETED로_전환하고_스테이징_원본을_삭제한다() throws IOException {
        // given
        Long albumId = 10L;
        Long memberId = 1L;
        Album album = processingAlbum(2);
        File firstTempFile = Files.createTempFile("staged_video_", ".mp4").toFile();
        List<AlbumVideoProcessingService.StagedVideoEntry> stagedEntries = List.of(
                new AlbumVideoProcessingService.StagedVideoEntry(0, "albums/staging/1/sid/0_abc.mp4", "video-0.mp4", "video/mp4"),
                new AlbumVideoProcessingService.StagedVideoEntry(1, "albums/staging/1/sid/1_def.mp4", "video-1.mp4", "video/mp4")
        );

        given(s3DirectUploadService.downloadToTempFile("albums/staging/1/sid/0_abc.mp4")).willReturn(firstTempFile);
        given(s3DirectUploadService.downloadToTempFile("albums/staging/1/sid/1_def.mp4"))
                .willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_FAILED));
        given(albumRepository.findById(albumId)).willReturn(Optional.of(album));

        // when
        albumVideoProcessingService.processStagedVideosAsync(albumId, memberId, stagedEntries);

        // then
        assertThat(album.getStatus()).isEqualTo(Status.DELETED);
        assertThat(firstTempFile).doesNotExist();
        then(s3DirectUploadService).should().deleteObject("albums/staging/1/sid/0_abc.mp4");
        then(s3DirectUploadService).should().deleteObject("albums/staging/1/sid/1_def.mp4");
        then(albumFileService).should(never()).uploadAlbumVideoWithThumbnail(any(MultipartFile.class), eq(memberId));
        then(eventPublisher).should(never()).publishEvent(any(AlbumCreatedEvent.class));
    }

    private Album processingAlbum(int mediaCount) {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01011112222");
        return Album.createAlbumForProcessing(
                member,
                "영상 앨범",
                placeholders(mediaCount, ""),
                placeholders(mediaCount, null),
                placeholders(mediaCount, null)
        );
    }

    private <T> List<T> placeholders(int size, T value) {
        List<T> values = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            values.add(value);
        }
        return values;
    }

    private AlbumVideoProcessingService.VideoEntry videoEntry(int index) throws IOException {
        File tempFile = Files.createTempFile("album_video_", ".mp4").toFile();
        Files.writeString(tempFile.toPath(), "video");
        return new AlbumVideoProcessingService.VideoEntry(index, tempFile, "video-" + index + ".mp4", "video/mp4");
    }
}
