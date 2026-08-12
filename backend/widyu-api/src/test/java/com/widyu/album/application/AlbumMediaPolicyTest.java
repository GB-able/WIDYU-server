package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("AlbumMediaPolicy 예외 처리 단위 테스트")
class AlbumMediaPolicyTest {

    private final AlbumMediaPolicy albumMediaPolicy = new AlbumMediaPolicy();

    @Test
    @DisplayName("파일 목록이 비어 있으면 FILE_IS_EMPTY 예외를 던진다")
    void 파일_목록이_비어_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> albumMediaPolicy.validate(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_IS_EMPTY)
                .hasMessageContaining("파일이 비어있습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 콘텐츠 타입이면 INVALID_FILE_TYPE 예외를 던진다")
    void 지원하지_않는_콘텐츠_타입이면_예외가_발생한다() {
        // given
        MultipartFile file = mockFile("application/pdf", 1024L);

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validate(List.of(file)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE)
                .hasMessageContaining("지원하지 않는 파일 형식입니다.");
    }

    @Test
    @DisplayName("업로드 개수 제한을 초과하면 BAD_REQUEST 예외를 던진다")
    void 업로드_개수_제한을_초과하면_예외가_발생한다() {
        // given
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 9)
                .mapToObj(i -> mockFile("image/jpeg", 1024L))
                .toList();

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validate(files))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("전체 최대 8개");
    }

    @Test
    @DisplayName("사진 크기 제한을 초과하면 FILE_TOO_LARGE 예외를 던진다")
    void 사진_크기_제한을_초과하면_예외가_발생한다() {
        // given
        MultipartFile file = mockFile("image/jpeg", AlbumMediaPolicy.MAX_PHOTO_BYTES + 1);

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validate(List.of(file)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_TOO_LARGE)
                .hasMessageContaining("사진은 최대 10MB");
    }

    @Test
    @DisplayName("메타데이터 검증에서 정상 파일 목록이면 예외가 발생하지 않는다")
    void 메타데이터_정상_파일_목록이면_예외가_발생하지_않는다() {
        // given
        List<AlbumMediaPolicy.MediaMetadata> files = List.of(
                new AlbumMediaPolicy.MediaMetadata("image/jpeg", 1024L),
                new AlbumMediaPolicy.MediaMetadata("video/mp4", 1024L * 1024L)
        );

        // when & then
        org.assertj.core.api.Assertions.assertThatCode(() -> albumMediaPolicy.validateMetadata(files))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("메타데이터 검증에서 파일 크기가 0 이하면 FILE_IS_EMPTY 예외를 던진다")
    void 메타데이터_파일_크기가_0_이하면_예외가_발생한다() {
        // given
        List<AlbumMediaPolicy.MediaMetadata> files = List.of(
                new AlbumMediaPolicy.MediaMetadata("image/jpeg", 0L)
        );

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validateMetadata(files))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_IS_EMPTY)
                .hasMessageContaining("파일 크기가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("메타데이터 검증에서 지원하지 않는 타입이면 INVALID_FILE_TYPE 예외를 던진다")
    void 메타데이터_지원하지_않는_타입이면_예외가_발생한다() {
        // given
        List<AlbumMediaPolicy.MediaMetadata> files = List.of(
                new AlbumMediaPolicy.MediaMetadata("application/pdf", 1024L)
        );

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validateMetadata(files))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    @DisplayName("메타데이터 검증에서 동영상 개수를 초과하면 BAD_REQUEST 예외를 던진다")
    void 메타데이터_동영상_개수를_초과하면_예외가_발생한다() {
        // given
        List<AlbumMediaPolicy.MediaMetadata> files = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> new AlbumMediaPolicy.MediaMetadata("video/mp4", 1024L))
                .toList();

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validateMetadata(files))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST)
                .hasMessageContaining("동영상 최대 3개");
    }

    @Test
    @DisplayName("메타데이터 검증에서 동영상 크기 제한을 초과하면 FILE_TOO_LARGE 예외를 던진다")
    void 메타데이터_동영상_크기_제한을_초과하면_예외가_발생한다() {
        // given
        List<AlbumMediaPolicy.MediaMetadata> files = List.of(
                new AlbumMediaPolicy.MediaMetadata("video/mp4", AlbumMediaPolicy.MAX_VIDEO_BYTES + 1)
        );

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validateMetadata(files))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_TOO_LARGE)
                .hasMessageContaining("동영상은 최대 2GB");
    }

    private MultipartFile mockFile(String contentType, long size) {
        MultipartFile file = mock(MultipartFile.class);
        given(file.getContentType()).willReturn(contentType);
        given(file.getSize()).willReturn(size);
        return file;
    }
}
