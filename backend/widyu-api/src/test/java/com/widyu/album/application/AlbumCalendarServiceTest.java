package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumCalendarRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.application.FamilyAccessService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumCalendarService 단위 테스트")
class AlbumCalendarServiceTest {

    @Mock private AlbumCalendarRepository albumRepository;
    @Mock private AlbumViewRepository albumViewRepository;
    @Mock private MemberUtil memberUtil;
    @Mock private FamilyAccessService familyAccessService;

    @InjectMocks
    private AlbumCalendarService albumCalendarService;

    @Test
    @DisplayName("앨범 캘린더를 조회하면 가족의 앨범이 있는 날짜를 반환한다")
    void 앨범_캘린더를_조회하면_가족의_앨범이_있는_날짜를_반환한다() {
        // given
        Member currentMember = org.mockito.Mockito.mock(Member.class);
        Album firstAlbum = org.mockito.Mockito.mock(Album.class);
        Album secondAlbum = org.mockito.Mockito.mock(Album.class);
        Album duplicateDayAlbum = org.mockito.Mockito.mock(Album.class);
        List<Long> familyMemberIds = List.of(1L, 2L, 3L);
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 1, 0, 0);

        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(familyAccessService.getFamilyMemberIds(currentMember)).willReturn(familyMemberIds);
        given(albumRepository.findAllByMemberIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
                familyMemberIds, start, end, Status.ACTIVE
        )).willReturn(List.of(firstAlbum, secondAlbum, duplicateDayAlbum));
        given(firstAlbum.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 10, 12, 0));
        given(secondAlbum.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 2, 12, 0));
        given(duplicateDayAlbum.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 10, 18, 0));

        // when
        List<Integer> days = albumCalendarService.getDaysWithEvents(2026, 8);

        // then
        assertThat(days).containsExactly(2, 10);
        then(albumRepository).should()
                .findAllByMemberIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
                        familyMemberIds, start, end, Status.ACTIVE
                );
    }
}
