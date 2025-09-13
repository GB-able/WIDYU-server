package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.FamilyAlbumPageResponse;
import com.widyu.domain.album.dto.FamilyAlbumResponse;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.repository.AlbumCalendarRepository;
import com.widyu.domain.album.repository.AlbumViewRepository;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import com.widyu.global.util.MemberUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlbumCalendarService {

    private final AlbumCalendarRepository albumRepository;
    private final AlbumViewRepository albumViewRepository;
    private final MemberUtil memberUtil;

    public List<Integer> getDaysWithEvents(int year, int month) {
        Member member = memberUtil.getCurrentMember();
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        return albumRepository.findAllByMemberAndCreatedAtBetweenAndStatus(
                        member, start, end, Status.ACTIVE)
                .stream()
                .map(album -> album.getCreatedAt().getDayOfMonth())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public FamilyAlbumPageResponse getFamilyAlbumsByDateWithCursor(LocalDate date, Long cursor) {
        Member member = memberUtil.getCurrentMember();
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        int pageSize = 5;
        int fetchSize = pageSize + 1;

        List<Album> albums = albumRepository.findFamilyAlbumsByDateRangeWithCursor(
                member.getId(), start, end, cursor, fetchSize, Status.ACTIVE);

        if (albums.isEmpty()) {
            return FamilyAlbumPageResponse.empty();
        }

        boolean hasNext = albums.size() > pageSize;
        List<Album> pageAlbums = hasNext ? albums.subList(0, pageSize) : albums;

        List<FamilyAlbumResponse> albumResponses = pageAlbums.stream()
                .map(album -> {
                    List<Member> viewers = albumViewRepository.findMembersByAlbumId(album.getId());
                    return FamilyAlbumResponse.from(album, viewers);
                })
                .collect(Collectors.toList());

        Long nextCursor = hasNext ? pageAlbums.get(pageSize - 1).getId() : null;

        return FamilyAlbumPageResponse.of(albumResponses, hasNext, nextCursor);
    }
}
