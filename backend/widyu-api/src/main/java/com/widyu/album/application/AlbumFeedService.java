package com.widyu.album.application;

import com.widyu.album.dto.request.AlbumFeedRequest;
import com.widyu.album.dto.response.AlbumFeedResponse;
import com.widyu.album.dto.response.AlbumMediaResponse;
import com.widyu.album.Album;
import com.widyu.album.repository.AlbumLikeRepository;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.member.Member;
import com.widyu.global.dto.CursorPage;
import com.widyu.global.util.MemberUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumFeedService {

    private static final int ALBUM_FEED_SIZE = 10;
    private static final int MEDIA_FEED_SIZE = 10;

    private final AlbumRepository albumRepository;
    private final AlbumLikeRepository albumLikeRepository;
    private final AlbumViewRepository albumViewRepository;
    private final MemberUtil memberUtil;

    @Transactional(readOnly = true)
    public CursorPage<AlbumFeedResponse> getAlbumFeed(AlbumFeedRequest request) {
        final Member currentMember = memberUtil.getCurrentMember();

        // 1) 앨범 ID 커서 페이지 조회 (size+1 로 hasNext 판단)
        Pageable pageable = pagePlusOne(ALBUM_FEED_SIZE);
        Slice<Long> idSlice = findAlbumIdSlice(request.hasCursor() ? request.lastAlbumId() : null, request.hasDate() ? LocalDate.parse(request.date()) : null, pageable);

        // 2) 상세 조회 (ID 순서 보존)
        List<Long> albumIds = idSlice.getContent();
        List<Album> albums = findAlbumsOrderedByIds(albumIds);

        // 3) 벌크 변환
        List<AlbumFeedResponse> feed = convertToFeedResponsesBulk(albums, currentMember);

        // 4) 트리밍 & 커서/hasNext
        boolean hasNext = idSlice.hasNext();
        if (feed.size() > ALBUM_FEED_SIZE) {
            feed = feed.subList(0, ALBUM_FEED_SIZE);
        }
        String nextCursor = lastAlbumIdOrNull(feed);

        return new CursorPage<>(feed, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public CursorPage<AlbumMediaResponse> getMediaFeed(Long lastPostId) {
        Pageable pageable = pagePlusOne(MEDIA_FEED_SIZE);

        // 1) 앨범 ID 커서 페이지
        Slice<Long> idSlice = findAlbumIdSlice(lastPostId, null, pageable);

        // 2) 상세 조회 (ID 순서 보존)
        List<Long> albumIds = idSlice.getContent();
        List<Album> albums = findAlbumsOrderedByIds(albumIds);

        // 3) 앨범 -> 미디어 평탄화
        List<AlbumMediaResponse> albumMediaResponses = new ArrayList<>();
        for (Album album : albums) {
            albumMediaResponses.addAll(AlbumMediaResponse.fromAlbum(album));
        }

        // 4) 커서는 마지막 미디어의 postId(=albumId)
        boolean hasNext = idSlice.hasNext();
        String nextCursor = albumMediaResponses.isEmpty()
                ? null
                : String.valueOf(albumMediaResponses.getLast().postId());

        return new CursorPage<>(albumMediaResponses, nextCursor, hasNext);
    }

    private Pageable pagePlusOne(int size) {
        return PageRequest.of(0, size + 1);
    }

    private Slice<Long> findAlbumIdSlice(Long lastPostId, LocalDate date, Pageable pageable) {
        if (date != null) {
            return (lastPostId != null)
                    ? albumRepository.findAlbumIdsAfterPostIdByDate(lastPostId, date, pageable)
                    : albumRepository.findLatestAlbumIdsByDate(date, pageable);
        } else {
            return (lastPostId != null)
                    ? albumRepository.findAlbumIdsAfterPostId(lastPostId, pageable)
                    : albumRepository.findLatestAlbumIds(pageable);
        }
    }

    private List<Album> findAlbumsOrderedByIds(List<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) return List.of();

        Map<Long, Integer> order = new HashMap<>(albumIds.size());
        for (int i = 0; i < albumIds.size(); i++) {
            order.put(albumIds.get(i), i);
        }

        List<Album> fetched = albumRepository.findAlbumsWithCollectionsByIds(albumIds);
        // 순서 보존 정렬
        fetched.sort(Comparator.comparingInt(a -> order.getOrDefault(a.getId(), Integer.MAX_VALUE)));
        return fetched;
    }

    private String lastAlbumIdOrNull(List<AlbumFeedResponse> feed) {
        if (feed == null || feed.isEmpty()) return null;
        AlbumFeedResponse last = feed.getLast();
        return String.valueOf(last.albumId());
    }

    private List<AlbumFeedResponse> convertToFeedResponsesBulk(List<Album> albums, Member currentMember) {
        if (albums == null || albums.isEmpty()) return List.of();

        List<Long> albumIds = albums.stream()
                .map(Album::getId)
                .collect(Collectors.toList());

        // canEdit: 현재 사용자가 작성한 앨범인지 확인

        Map<Long, List<AlbumFeedResponse.ViewerInfo>> viewersMap = albumViewRepository
                .findTop3ViewersForAlbums(albumIds).stream()
                .collect(Collectors.groupingBy(
                        view -> view.getAlbum().getId(),
                        Collectors.mapping(
                                view -> new AlbumFeedResponse.ViewerInfo(
                                        view.getMember().getName(),
                                        null // TODO: 프로필 이미지 경로 추가 시 반영
                                ),
                                Collectors.toList()
                        )
                ));

        // 순서대로 응답 생성
        List<AlbumFeedResponse> result = new ArrayList<>(albums.size());
        for (Album album : albums) {
            boolean canEdit = album.getMember().getId().equals(currentMember.getId());
            List<AlbumFeedResponse.ViewerInfo> viewers =
                    viewersMap.getOrDefault(album.getId(), List.of());
            result.add(AlbumFeedResponse.from(album, canEdit, viewers));
        }
        return result;
    }
}
