package com.widyu.album.application;

import com.widyu.album.Album;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlbumPermissionService {

    private final AlbumUnlockService albumUnlockService;

    public void checkViewPermission(Album album, Member me) {
        switch (me) {
            // 내가 쓴 앨범은 항상 허용
            case Member m when album.getMember().getId().equals(m.getId()) -> {
            }

            // 보호자(GUARDIAN)는 항상 허용
            case Member m when m.getType() == MemberType.GUARDIAN -> {
            }

            // 부모(PARENT)인데 해금되어 있으면 허용
            case Member m when m.getType() == MemberType.PARENT
                    && albumUnlockService.isAlbumUnlocked(album, m) -> {
            }

            // 나머지는 거부
            default -> throw new BusinessException(ErrorCode.FORBIDDEN, "앨범 해금이 필요합니다.");
        }
    }
}