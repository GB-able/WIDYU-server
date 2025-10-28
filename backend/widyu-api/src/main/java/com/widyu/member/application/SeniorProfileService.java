package com.widyu.member.application;

import com.widyu.album.dto.response.UnlockedAlbumIdsResponse;
import com.widyu.album.repository.AlbumUnlockRepository;
import com.widyu.member.dto.response.SeniorPointsResponse;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
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
public class SeniorProfileService {

    private final MemberUtil memberUtil;
    private final AlbumUnlockRepository albumUnlockRepository;

    @Transactional(readOnly = true)
    public SeniorPointsResponse getLeftPoints() {
        Member currentMember = memberUtil.getCurrentMember();

        // 시니어 타입 검증 (SENIOR 또는 PARENT 허용 - 하위 호환)
        if (currentMember.getType() != MemberType.SENIOR && currentMember.getType() != MemberType.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "시니어 회원만 접근할 수 있습니다.");
        }

        SeniorProfile seniorProfile = currentMember.getSeniorProfile();
        if (seniorProfile == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "시니어 프로필을 찾을 수 없습니다.");
        }

        log.info("시니어 포인트 조회: memberId={}, points={}", currentMember.getId(), seniorProfile.getPoints());
        return SeniorPointsResponse.from(seniorProfile);
    }

    @Transactional(readOnly = true)
    public UnlockedAlbumIdsResponse getUnlockedAlbums() {
        Member currentMember = memberUtil.getCurrentMember();

        // 시니어 타입 검증
        if (currentMember.getType() != MemberType.SENIOR && currentMember.getType() != MemberType.PARENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "시니어 회원만 접근할 수 있습니다.");
        }

        // 해금한 앨범 ID 목록 조회
        List<Long> unlockedAlbumIds = albumUnlockRepository.findUnlockedAlbumIdsByMember(currentMember);

        log.info("해금된 앨범 ID 조회: memberId={}, count={}",
                currentMember.getId(), unlockedAlbumIds.size());

        return UnlockedAlbumIdsResponse.from(unlockedAlbumIds);
    }
}
