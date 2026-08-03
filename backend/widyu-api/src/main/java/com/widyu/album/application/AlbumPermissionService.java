package com.widyu.album.application;

import com.widyu.album.Album;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlbumPermissionService {

    private final AlbumUnlockService albumUnlockService;
    private final FamilyAccessService familyAccessService;

    public void checkFamilyAccess(Album album, Member me) {
        if (album.getMember().getId().equals(me.getId())) {
            return;
        }
        familyAccessService.verifySameFamily(me, album.getMember());
    }

    public void checkViewPermission(Album album, Member me) {
        checkFamilyAccess(album, me);
        switch (me) {
            // 내가 쓴 앨범은 항상 허용
            case Member m when album.getMember().getId().equals(m.getId()) -> {
            }

            // 같은 가족의 보호자(GUARDIAN)는 항상 허용
            case Member m when m.getType() == MemberType.GUARDIAN -> {
            }

            // 시니어(SENIOR)인데 해금되어 있으면 허용
            case Member m when m.getType() == MemberType.SENIOR
                    && albumUnlockService.isAlbumUnlocked(album, m) -> {
            }

            // 나머지는 거부
            default -> throw new BusinessException(ErrorCode.ALBUM_UNLOCK_REQUIRED);
        }
    }
}
