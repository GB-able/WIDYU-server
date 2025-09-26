package com.widyu.domain.album.application;

import com.widyu.domain.album.dto.response.AlbumUnlockResponse;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumUnlock;
import com.widyu.domain.album.repository.AlbumRepository;
import com.widyu.domain.album.repository.AlbumUnlockRepository;
import com.widyu.domain.member.entity.Member;
import com.widyu.domain.member.entity.ParentProfile;
import com.widyu.global.domain.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumUnlockService {

    private final AlbumUnlockRepository albumUnlockRepository;
    private final AlbumRepository albumRepository;
    private final MemberUtil memberUtil;

    private static final long DEFAULT_UNLOCK_PRICE = 50;

    @Transactional
    public AlbumUnlockResponse unlockAlbum(Long albumId) {
        Member currentMember = memberUtil.getCurrentMember();

        Album album = albumRepository.findByIdAndStatus(albumId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

        // 1. 본인 앨범 해금 방지
        if (album.getMember().getId().equals(currentMember.getId())) {
            throw new BusinessException(ErrorCode.ALBUM_UNLOCK_SELF_NOT_ALLOWED);
        }

        // 2. 이미 해금된 앨범인지 확인
        if (albumUnlockRepository.existsByAlbumAndMember(album, currentMember)) {
            throw new BusinessException(ErrorCode.ALBUM_ALREADY_UNLOCKED);
        }

        ParentProfile parentProfile = currentMember.getParentProfile();

        // 3. 포인트 잔액 확인
        if (!hasEnoughBalance(parentProfile)) {
            throw new BusinessException(ErrorCode.ALBUM_UNLOCK_INSUFFICIENT_BALANCE);
        }

        // 4. 포인트 차감 (임시 구현 - 실제로는 포인트 시스템과 연동)
        deductPoints(parentProfile);

        // 5. 해금 기록 생성
        AlbumUnlock albumUnlock = AlbumUnlock.createUnlock(album, currentMember);
        AlbumUnlock savedUnlock = albumUnlockRepository.save(albumUnlock);

        return AlbumUnlockResponse.from(savedUnlock);
    }

    @Transactional(readOnly = true)
    public boolean isAlbumUnlocked(Album album, Member member) {
        return albumUnlockRepository.existsByAlbumAndMember(album, member);
    }

    private boolean hasEnoughBalance(ParentProfile parentProfile) {
        Long points = parentProfile.getPoints();
        return points >= DEFAULT_UNLOCK_PRICE;
    }

    private void deductPoints(ParentProfile parentProfile) {
        parentProfile.deductPoints(DEFAULT_UNLOCK_PRICE);
    }
}
