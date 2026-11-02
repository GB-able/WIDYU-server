package com.widyu.album.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.album.Album;
import com.widyu.album.MediaType;
import com.widyu.album.dto.FamilyAlbumResponse;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("앨범 응답의 미디어별 타입 mediaTypes (#948)")
class AlbumMediaTypesResponseTest {

    private static final String PHOTO_URL = "https://cdn.widyu.shop/albums/photos/1/p.jpg";
    private static final String VIDEO_URL = "https://cdn.widyu.shop/albums/videos/1/v.mp4";
    private static final String VIDEO_THUMB = "https://cdn.widyu.shop/albums/thumbnails/1/v_thumbnail.jpg";

    private static Album photoThenVideoAlbum() {
        Member author = Member.createMember(MemberType.GUARDIAN, "보호자", "01000000000");
        ReflectionTestUtils.setField(author, "id", 1L);
        Album album = Album.createAlbumWithMetadata(author, "내용",
                new ArrayList<>(List.of(PHOTO_URL, VIDEO_URL)),
                new ArrayList<>(List.of(PHOTO_URL, VIDEO_THUMB)),
                new ArrayList<>(Arrays.asList(null, 12)));
        ReflectionTestUtils.setField(album, "id", 10L);
        return album;
    }

    @Test
    @DisplayName("해금한 앨범을 조회하면 세 응답 모두 mediaUrls 순서대로 미디어 타입을 담는다")
    void 해금한_앨범을_조회하면_미디어_순서대로_타입을_담는다() {
        // given
        Album album = photoThenVideoAlbum();

        // when
        AlbumFeedResponse feed = AlbumFeedResponse.from(album, false, true, List.of());
        AlbumDetailResponse detail = AlbumDetailResponse.from(album, 99L, List.of(), List.of(), true);
        FamilyAlbumResponse calendar = FamilyAlbumResponse.from(album, List.of(), true);

        // then
        assertThat(feed.mediaTypes()).containsExactly(MediaType.PHOTO, MediaType.VIDEO);
        assertThat(detail.mediaTypes()).containsExactly(MediaType.PHOTO, MediaType.VIDEO);
        assertThat(calendar.mediaTypes()).containsExactly(MediaType.PHOTO, MediaType.VIDEO);
    }

    @Test
    @DisplayName("사진이 먼저인 잠금 전 앨범을 조회하면 대표 썸네일과 같은 첫 미디어 타입 하나만 담는다")
    void 사진이_먼저인_잠금_전_앨범을_조회하면_첫_미디어_타입_하나만_담는다() {
        // given
        Album album = photoThenVideoAlbum();

        // when
        AlbumFeedResponse feed = AlbumFeedResponse.from(album, false, false, List.of());
        AlbumDetailResponse detail = AlbumDetailResponse.from(album, 99L, List.of(), List.of(), false);
        FamilyAlbumResponse calendar = FamilyAlbumResponse.from(album, List.of(), false);

        // then
        assertThat(feed.thumbnailUrls()).containsExactly(PHOTO_URL);
        assertThat(feed.mediaTypes()).containsExactly(MediaType.PHOTO);
        assertThat(feed.primaryMediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(detail.mediaTypes()).containsExactly(MediaType.PHOTO);
        assertThat(calendar.mediaTypes()).containsExactly(MediaType.PHOTO);
    }
}
