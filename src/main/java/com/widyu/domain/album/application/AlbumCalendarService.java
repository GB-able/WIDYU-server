package com.widyu.domain.album.application;

import com.widyu.domain.album.repository.AlbumCalendarRepository;
import com.widyu.domain.member.entity.Member;
import com.widyu.global.domain.Status;
import com.widyu.global.util.MemberUtil;
import java.time.LocalDateTime;
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
    private final MemberUtil memberUtil;

    public List<Integer> getDaysWithEvents(int year, int month) {
        Member member = memberUtil.getCurrentMember(); // 로그인한 사용자
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
}
