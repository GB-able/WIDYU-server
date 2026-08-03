package com.widyu.album.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.album.dto.request.AlbumFeedRequest;
import com.widyu.album.repository.AlbumLikeRepository;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.application.FamilyAccessService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumFeedService 단위 테스트")
class AlbumFeedServiceTest {

    @Mock private AlbumRepository albumRepository;
    @Mock private AlbumLikeRepository albumLikeRepository;
    @Mock private AlbumViewRepository albumViewRepository;
    @Mock private MemberUtil memberUtil;
    @Mock private FamilyAccessService familyAccessService;

    @InjectMocks
    private AlbumFeedService albumFeedService;

    @Test
    @DisplayName("앨범 피드는 현재 사용자의 가족 구성원 ID로 조회한다")
    void 앨범_피드는_가족_구성원_ID로_조회한다() {
        // given
        Member currentMember = org.mockito.Mockito.mock(Member.class);
        List<Long> familyMemberIds = List.of(1L, 2L, 3L);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(familyAccessService.getFamilyMemberIds(currentMember)).willReturn(familyMemberIds);
        given(albumRepository.findLatestAlbumIdsByMemberIds(eq(familyMemberIds), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        // when
        albumFeedService.getAlbumFeed(AlbumFeedRequest.from(null, null));

        // then
        then(albumRepository).should().findLatestAlbumIdsByMemberIds(eq(familyMemberIds), any(Pageable.class));
    }

    @Test
    @DisplayName("미디어 피드는 현재 사용자의 가족 구성원 ID로 조회한다")
    void 미디어_피드는_가족_구성원_ID로_조회한다() {
        // given
        Member currentMember = org.mockito.Mockito.mock(Member.class);
        List<Long> familyMemberIds = List.of(1L, 2L, 3L);
        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(familyAccessService.getFamilyMemberIds(currentMember)).willReturn(familyMemberIds);
        given(albumRepository.findLatestAlbumIdsByMemberIds(eq(familyMemberIds), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        // when
        albumFeedService.getMediaFeed(null, null);

        // then
        then(albumRepository).should().findLatestAlbumIdsByMemberIds(eq(familyMemberIds), any(Pageable.class));
    }
}
