package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.album.Album;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlbumPermissionService 단위 테스트")
class AlbumPermissionServiceTest {

    @Mock
    private AlbumUnlockService albumUnlockService;

    @InjectMocks
    private AlbumPermissionService albumPermissionService;

    @Test
    @DisplayName("앨범 작성자 본인이 조회하면 권한 검사를 통과한다")
    void 앨범_작성자_본인_조회_시_권한_통과() {
        // given
        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);

        // when & then
        assertThatNoException().isThrownBy(() -> albumPermissionService.checkViewPermission(album, owner));
    }

    @Test
    @DisplayName("보호자(GUARDIAN)는 해금 없이 모든 앨범을 조회할 수 있다")
    void 보호자는_해금_없이_앨범_조회_가능() {
        // given
        Member guardian = mock(Member.class);
        given(guardian.getId()).willReturn(2L);
        given(guardian.getType()).willReturn(MemberType.GUARDIAN);

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);

        // when & then
        assertThatNoException().isThrownBy(() -> albumPermissionService.checkViewPermission(album, guardian));
    }

    @Test
    @DisplayName("해금된 앨범은 시니어(SENIOR)가 조회할 수 있다")
    void 해금된_앨범은_시니어가_조회_가능() {
        // given
        Member senior = mock(Member.class);
        given(senior.getId()).willReturn(2L);
        given(senior.getType()).willReturn(MemberType.SENIOR);

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);
        given(albumUnlockService.isAlbumUnlocked(album, senior)).willReturn(true);

        // when & then
        assertThatNoException().isThrownBy(() -> albumPermissionService.checkViewPermission(album, senior));
    }

    @Test
    @DisplayName("해금하지 않은 앨범을 시니어가 조회하면 ALBUM_UNLOCK_REQUIRED 예외를 던진다")
    void 미해금_앨범_시니어_조회_시_예외가_발생한다() {
        // given
        Member senior = mock(Member.class);
        given(senior.getId()).willReturn(2L);
        given(senior.getType()).willReturn(MemberType.SENIOR);

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);
        given(albumUnlockService.isAlbumUnlocked(album, senior)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> albumPermissionService.checkViewPermission(album, senior))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_UNLOCK_REQUIRED);
    }
}
