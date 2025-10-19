package com.widyu.album.application;

import com.widyu.album.dto.response.AlbumUnlockResponse;
import com.widyu.album.Album;
import com.widyu.album.AlbumUnlock;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumUnlockRepository;
import com.widyu.fcm.event.album.dto.AlbumUnlockedEvent;
import com.widyu.member.Member;
import com.widyu.member.ParentProfile;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlbumUnlockService {

    private final AlbumUnlockRepository albumUnlockRepository;
    private final AlbumRepository albumRepository;
    private final MemberUtil memberUtil;
    private final ApplicationEventPublisher eventPublisher;

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

        // 앨범 잠금 해제 알림 이벤트 발행
        eventPublisher.publishEvent(new AlbumUnlockedEvent(
                albumId,
                currentMember.getId()
        ));

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
