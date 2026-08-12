package com.widyu.album;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("Album 영상 처리 완료 영속화 테스트")
class AlbumVideoProcessingPersistenceTest {

    @Autowired private TestEntityManager entityManager;
    @MockBean private JPAQueryFactory jpaQueryFactory;

    @Test
    @DisplayName("null 메타데이터 placeholder로 저장된 영상 앨범도 처리 완료할 수 있다")
    void null_메타데이터_placeholder로_저장된_영상_앨범도_처리_완료할_수_있다() {
        // given
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01011112222");
        entityManager.persist(member);
        Album album = Album.createAlbumForProcessing(
                member, "영상 앨범", List.of(""), nullPlaceholder(), nullPlaceholder());
        entityManager.persistAndFlush(album);
        Long albumId = album.getId();
        entityManager.clear();

        Album processingAlbum = entityManager.find(Album.class, albumId);

        // when
        processingAlbum.completeVideoProcessing(
                Map.of(0, "https://cdn/video.mp4"),
                Map.of(0, "https://cdn/thumb.jpg"),
                Map.of(0, 10));
        entityManager.flush();
        entityManager.clear();

        // then
        Album completedAlbum = entityManager.find(Album.class, albumId);
        assertThat(completedAlbum.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(completedAlbum.getMediaUrls()).containsExactly("https://cdn/video.mp4");
        assertThat(completedAlbum.getThumbnailUrls()).containsExactly("https://cdn/thumb.jpg");
        assertThat(completedAlbum.getDurations()).containsExactly(10);
    }

    private <T> List<T> nullPlaceholder() {
        List<T> values = new ArrayList<>();
        values.add(null);
        return values;
    }
}
