package com.widyu.album.application;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.widyu.album.Album;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.application.FamilyAccessService;
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

    @Mock
    private FamilyAccessService familyAccessService;

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
    @DisplayName("같은 가족의 보호자(GUARDIAN)는 해금 없이 앨범을 조회할 수 있다")
    void 보호자는_해금_없이_앨범_조회_가능() {
        // given
        Member guardian = mock(Member.class);
        given(guardian.getId()).willReturn(2L);
        given(guardian.getType()).willReturn(MemberType.GUARDIAN);

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);
        allowFamilyAccess(guardian, owner);

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
        given(album.requiresUnlock()).willReturn(true);
        given(albumUnlockService.isAlbumUnlocked(album, senior)).willReturn(true);
        allowFamilyAccess(senior, owner);

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
        given(album.requiresUnlock()).willReturn(true);
        given(albumUnlockService.isAlbumUnlocked(album, senior)).willReturn(false);
        allowFamilyAccess(senior, owner);

        // when & then
        assertThatThrownBy(() -> albumPermissionService.checkViewPermission(album, senior))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALBUM_UNLOCK_REQUIRED);
    }

    @Test
    @DisplayName("가족 외 앨범을 보호자가 조회하면 FORBIDDEN 예외를 던진다")
    void 가족_외_앨범_보호자_조회_시_예외가_발생한다() {
        // given
        Member guardian = mock(Member.class);
        given(guardian.getId()).willReturn(2L);

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);
        willThrow(new BusinessException(ErrorCode.FORBIDDEN)).given(familyAccessService)
                .verifySameFamily(guardian, owner);

        // when & then
        assertThatThrownBy(() -> albumPermissionService.checkViewPermission(album, guardian))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("시니어가 올린 앨범은 같은 가족의 다른 시니어가 해금 없이 조회할 수 있다")
    void 시니어가_올린_앨범은_다른_시니어가_해금_없이_조회_가능() {
        // given
        Member senior = mock(Member.class);
        given(senior.getId()).willReturn(2L);
        given(senior.getType()).willReturn(MemberType.SENIOR);

        Member owner = mock(Member.class);
        given(owner.getId()).willReturn(1L);

        Album album = mock(Album.class);
        given(album.getMember()).willReturn(owner);
        given(album.requiresUnlock()).willReturn(false);
        allowFamilyAccess(senior, owner);

        // when & then
        assertThatNoException().isThrownBy(() -> albumPermissionService.checkViewPermission(album, senior));
    }

    private void allowFamilyAccess(Member member, Member targetMember) {
        willDoNothing().given(familyAccessService).verifySameFamily(member, targetMember);
    }
}
