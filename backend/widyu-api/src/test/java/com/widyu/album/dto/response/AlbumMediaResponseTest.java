package com.widyu.album.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.album.Album;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("앨범 미디어 응답의 사진, 동영상 구분")
class AlbumMediaResponseTest {

    @Test
    @DisplayName("아이폰 HEIC, HEIF 사진 URL이면 확장자 대소문자와 관계없이 사진으로 응답하고 사진 개수에 센다")
    void 아이폰_HEIC_HEIF_사진_URL이면_사진으로_응답한다() {
        // given: 아이폰 원본 파일 이름은 IMG_0001.HEIC처럼 확장자가 대문자다(#1041)
        String heic = "https://cdn.widyu.shop/albums/photos/1/IMG_0001.HEIC";
        String heif = "https://cdn.widyu.shop/albums/photos/1/IMG_0002.heif";
        Member author = Member.createMember(MemberType.GUARDIAN, "보호자", "01000000000");
        ReflectionTestUtils.setField(author, "id", 1L);
        Album album = Album.createAlbumWithMetadata(author, "내용",
                new ArrayList<>(List.of(heic, heif)),
                new ArrayList<>(List.of(heic, heif)),
                new ArrayList<>(Arrays.asList(null, null)));
        ReflectionTestUtils.setField(album, "id", 10L);

        // when
        List<AlbumMediaResponse> responses = AlbumMediaResponse.fromAlbum(album, true);

        // then
        assertThat(responses).extracting(AlbumMediaResponse::type).containsExactly("image", "image");
        assertThat(responses).extracting(AlbumMediaResponse::duration).containsExactly(null, null);
        assertThat(album.getPhotoCount()).isEqualTo(2);
        assertThat(album.getVideoCount()).isZero();
    }
}
