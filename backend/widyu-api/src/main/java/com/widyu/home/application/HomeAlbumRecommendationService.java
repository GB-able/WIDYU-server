package com.widyu.home.application;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.member.Member;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeAlbumRecommendationService {

    private static final int ALBUM_CANDIDATE_SIZE = 10;
    private static final int ALBUM_RESULT_SIZE = 3;
    private static final int LIKE_WEIGHT = 3;
    private static final int COMMENT_WEIGHT = 2;
    private static final int DATE_BONUS = 10;

    private final AlbumRepository albumRepository;
    private final FamilyMemberQueryService familyMemberQueryService;

    public List<Album> recommendAlbums(Member senior, LocalDate today) {
        List<Long> familyMemberIds = familyMemberQueryService.getFamilyMemberIds(senior);

        List<Long> candidateIds = albumRepository
                .findTopScoredAlbumIdsByMemberIds(familyMemberIds, PageRequest.of(0, ALBUM_CANDIDATE_SIZE))
                .getContent();

        if (candidateIds.isEmpty()) {
            return List.of();
        }

        return albumRepository.findAlbumsWithCollectionsByIds(candidateIds).stream()
                .sorted(Comparator.comparingInt((Album album) -> calculateScore(album, today)).reversed())
                .limit(ALBUM_RESULT_SIZE)
                .toList();
    }

    private int calculateScore(Album album, LocalDate today) {
        int baseScore = album.getLikeCount() * LIKE_WEIGHT + album.getCommentCount() * COMMENT_WEIGHT;
        if (isAnniversary(album, today)) {
            return baseScore + DATE_BONUS;
        }
        return baseScore;
    }

    private boolean isAnniversary(Album album, LocalDate today) {
        LocalDate albumDate = album.getCreatedAt().toLocalDate();
        return albumDate.getMonth() == today.getMonth()
                && albumDate.getDayOfMonth() == today.getDayOfMonth();
    }
}
