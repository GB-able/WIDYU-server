package com.widyu.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class HomeAlbumRecommendationServiceTest {

    @Autowired private AlbumRepository albumRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    @DisplayName("가족 앨범이 12개면 보호자 홈 앨범은 처리 중·삭제 앨범을 빼고 최근 10개를 최신순으로 반환한다")
    void 보호자_홈_앨범은_최근_10개를_최신순으로_반환한다() {
        // given
        Member senior = member(MemberType.SENIOR, "어르신", "01022220001");
        Member uploader = member(MemberType.GUARDIAN, "보호자", "01022220002");
        List<Long> activeIds = new ArrayList<>();
        for (int day = 1; day <= 12; day++) {
            activeIds.add(album(uploader, LocalDateTime.of(2026, 10, day, 9, 0), "ACTIVE", 0));
        }
        album(uploader, LocalDateTime.of(2026, 10, 20, 9, 0), "PROCESSING", 0);
        album(uploader, LocalDateTime.of(2026, 10, 21, 9, 0), "DELETED", 0);
        entityManager.clear();
        HomeAlbumRecommendationService service = service(senior, uploader);

        // when
        List<Album> albums = service.findLatestAlbums(senior);

        // then
        assertThat(albums).extracting(Album::getId)
                .containsExactlyElementsOf(activeIds.reversed().subList(0, 10));
    }

    @Test
    @DisplayName("시니어 홈 앨범은 지금처럼 좋아요와 댓글 점수 상위 3개를 반환한다")
    void 시니어_홈_앨범은_점수_상위_3개를_반환한다() {
        // given
        Member senior = member(MemberType.SENIOR, "어르신", "01033330001");
        Member uploader = member(MemberType.GUARDIAN, "보호자", "01033330002");
        Long low = album(uploader, LocalDateTime.of(2026, 10, 1, 9, 0), "ACTIVE", 1);
        Long top = album(uploader, LocalDateTime.of(2026, 10, 2, 9, 0), "ACTIVE", 9);
        Long second = album(uploader, LocalDateTime.of(2026, 10, 3, 9, 0), "ACTIVE", 5);
        Long third = album(uploader, LocalDateTime.of(2026, 10, 4, 9, 0), "ACTIVE", 3);
        entityManager.clear();
        HomeAlbumRecommendationService service = service(senior, uploader);

        // when
        List<Album> albums = service.recommendAlbums(senior, LocalDate.of(2026, 11, 10));

        // then
        assertThat(albums).extracting(Album::getId).containsExactly(top, second, third);
        assertThat(albums).extracting(Album::getId).doesNotContain(low);
    }

    private HomeAlbumRecommendationService service(Member senior, Member uploader) {
        FamilyMemberQueryService familyMemberQueryService = mock(FamilyMemberQueryService.class);
        given(familyMemberQueryService.getFamilyMemberIds(senior))
                .willReturn(List.of(senior.getId(), uploader.getId()));
        return new HomeAlbumRecommendationService(albumRepository, familyMemberQueryService);
    }

    private Member member(MemberType type, String name, String phone) {
        return entityManager.persistAndFlush(Member.createMember(type, name, phone));
    }

    private Long album(Member writer, LocalDateTime createdAt, String status, int likeCount) {
        Album album = entityManager.persistAndFlush(
                Album.createAlbumWithMetadata(writer, "내용", List.of("m.jpg"), List.of("t.jpg"), List.of(0)));
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE album SET created_at = ?1, status = ?2, like_count = ?3 WHERE album_id = ?4")
                .setParameter(1, createdAt).setParameter(2, status).setParameter(3, likeCount)
                .setParameter(4, album.getId()).executeUpdate();
        return album.getId();
    }
}
