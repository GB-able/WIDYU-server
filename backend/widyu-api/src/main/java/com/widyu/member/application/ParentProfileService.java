package com.widyu.member.application;

import com.widyu.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.album.repository.AlbumUnlockRepository;
import com.widyu.member.dto.response.ParentPointsResponse;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.ParentProfile;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentProfileService {

    private final MemberUtil memberUtil;
    private final AlbumUnlockRepository albumUnlockRepository;

    @Transactional(readOnly = true)
    public ParentPointsResponse getLeftPoints() {
        Member currentMember = memberUtil.getCurrentMember();

        // 부모 타입 검증
        if (currentMember.getType() != MemberType.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "부모 회원만 접근할 수 있습니다.");
        }

        ParentProfile parentProfile = currentMember.getParentProfile();
        if (parentProfile == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "부모 프로필을 찾을 수 없습니다.");
        }

        log.info("부모 포인트 조회: memberId={}, points={}", currentMember.getId(), parentProfile.getPoints());
        return ParentPointsResponse.from(parentProfile);
    }

    @Transactional(readOnly = true)
    public UnlockedAlbumIdsResponse getUnlockedAlbums() {
        Member currentMember = memberUtil.getCurrentMember();

        // 부모 타입 검증
        if (currentMember.getType() != MemberType.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "부모 회원만 접근할 수 있습니다.");
        }

        // 해금한 앨범 ID 목록 조회
        List<Long> unlockedAlbumIds = albumUnlockRepository.findUnlockedAlbumIdsByMember(currentMember);

        log.info("해금된 앨범 ID 조회: memberId={}, count={}", 
                currentMember.getId(), unlockedAlbumIds.size());

        return UnlockedAlbumIdsResponse.from(unlockedAlbumIds);
    }
}