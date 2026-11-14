package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.io.ByteArrayInputStream;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
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

    @ParameterizedTest
    @ValueSource(strings = {"image/heic", "image/heif"})
    @DisplayName("아이폰 HEIC, HEIF 사진을 올리면 멀티파트와 업로드 세션 검증을 모두 통과한다")
    void 아이폰_HEIC_HEIF_사진을_올리면_검증을_통과한다(String contentType) {
        // given
        MultipartFile file = mockFile(contentType, 1024L);
        List<AlbumMediaPolicy.MediaMetadata> metadata = List.of(new AlbumMediaPolicy.MediaMetadata(contentType, 1024L));

        // when & then
        org.assertj.core.api.Assertions.assertThatCode(() -> albumMediaPolicy.validate(List.of(file)))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatCode(() -> albumMediaPolicy.validateMetadata(metadata))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HEIC 사진도 10MB를 넘으면 FILE_TOO_LARGE 예외를 던진다")
    void HEIC_사진도_10MB를_넘으면_예외가_발생한다() {
        // given
        MultipartFile file = mockFile("image/heic", AlbumMediaPolicy.MAX_PHOTO_BYTES + 1);

        // when & then
        assertThatThrownBy(() -> albumMediaPolicy.validate(List.of(file)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_TOO_LARGE);
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

    // ======================== 단일 사진 검사 (복약 인증, 프로필) ========================

    @ParameterizedTest
    @CsvSource({
            "FFD8FFE0, image/jpeg, jpg",
            "89504E470D0A1A0A, image/png, png",
            "474946383961, image/gif, gif",
            "524946460000000057454250, image/webp, webp",
            "424D, image/bmp, bmp",
            "0000001866747970686569630000, image/heic, heic",
            "00000018667479706D69663100, image/heif, heif"
    })
    @DisplayName("파일 앞부분 시그니처로 형식을 정하면 클라이언트 Content-Type과 무관하게 저장 형식과 확장자를 반환한다")
    void 파일_시그니처로_저장_형식과_확장자를_정한다(String hex, String contentType, String extension) {
        // given
        MultipartFile file = new MockMultipartFile("image", "a.html", "application/octet-stream", HexFormat.of().parseHex(hex));

        // when
        AlbumMediaPolicy.ImageFormat format = AlbumMediaPolicy.validateImageFile(file);

        // then
        assertThat(format).isEqualTo(new AlbumMediaPolicy.ImageFormat(contentType, extension));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>",
            "<html><script>alert(1)</script></html>",
            "plain text"
    })
    @DisplayName("이미지 Content-Type을 붙여도 시그니처가 허용 이미지가 아니면 INVALID_FILE_TYPE 예외를 던진다")
    void 시그니처가_허용_이미지가_아니면_예외가_발생한다(String body) {
        // given
        MultipartFile file = new MockMultipartFile("image", "a.png", "image/svg+xml", body.getBytes());

        // when & then
        assertThatThrownBy(() -> AlbumMediaPolicy.validateImageFile(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    @DisplayName("단일 사진이 앨범 사진 상한 10MB를 넘으면 FILE_TOO_LARGE 예외를 던진다")
    void 단일_사진이_10MB를_넘으면_예외가_발생한다() throws Exception {
        // given
        MultipartFile file = mock(MultipartFile.class);
        given(file.isEmpty()).willReturn(false);
        given(file.getInputStream()).willReturn(new ByteArrayInputStream(HexFormat.of().parseHex("FFD8FFE0")));
        given(file.getSize()).willReturn(AlbumMediaPolicy.MAX_PHOTO_BYTES + 1);

        // when & then
        assertThatThrownBy(() -> AlbumMediaPolicy.validateImageFile(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("빈 파일을 단일 사진으로 올리면 FILE_IS_EMPTY 예외를 던진다")
    void 빈_파일을_단일_사진으로_올리면_예외가_발생한다() {
        // given
        MultipartFile file = new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]);

        // when & then
        assertThatThrownBy(() -> AlbumMediaPolicy.validateImageFile(file))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_IS_EMPTY);
    }

    private MultipartFile mockFile(String contentType, long size) {
        MultipartFile file = mock(MultipartFile.class);
        given(file.getContentType()).willReturn(contentType);
        given(file.getSize()).willReturn(size);
        return file;
    }
}
