package com.widyu.domain.fcm.event.album.listener;

import com.widyu.domain.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.domain.album.repository.AlbumViewRepository;
import com.widyu.domain.fcm.application.FcmService;
import com.widyu.domain.fcm.api.dto.FcmSendDto;
import com.widyu.domain.fcm.domain.FcmCategory;
import com.widyu.domain.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.domain.member.entity.Member;
import com.widyu.domain.member.entity.ParentProfile;
import com.widyu.domain.member.repository.MemberRepository;
import com.widyu.domain.member.repository.ParentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AlbumNotificationListener {

    private final FcmService fcmService;
    private final ParentProfileRepository parentProfileRepository;
    private final MemberRepository memberRepository;
    private final AlbumViewRepository albumViewRepository;

    // 게시물 작성시 부모님께 알림 발송
    @Async
    @EventListener
    public void handleAlbumCreated(AlbumCreatedEvent event) {
        // 보호자 정보 조회
        Member guardian = memberRepository.findById(event.authorId())
                .orElse(null);
        if (guardian == null) {
            return;
        }

        // 보호자의 부모님 목록 조회
        List<ParentProfile> parentProfiles = parentProfileRepository.findAllByGuardianId(event.authorId());

        // 각 부모님에게 알림 발송
        for (ParentProfile parentProfile : parentProfiles) {
            FcmSendDto dto = new FcmSendDto(
                    guardian.getName() + "님이 새로운 소식을 전했어요!",
                    "새로운 앨범을 확인해보세요.",
                    FcmCategory.ALBUM
                    ,""
            );
            fcmService.sendMessageToUser(parentProfile.getMember().getId(), dto);
        }
    }

    // 게시물 조회시 모든 게시물 확인했는지 체크 및 알림 발송
    @Async
    @EventListener
    public void handleAlbumViewed(AlbumViewedEvent event) {
        Member parentMember = memberRepository.findById(event.memberId())
                .orElse(null);
        if (parentMember == null) {
            return;
        }

        // 부모님 프로필 조회
        List<ParentProfile> parentProfiles = parentProfileRepository.findAll()
                .stream()
                .filter(pp -> pp.getMember().getId().equals(event.memberId()))
                .collect(Collectors.toList());

        if (parentProfiles.isEmpty()) {
            return;
        }

        ParentProfile parentProfile = parentProfiles.getFirst();

        // 보호자들 ID 조회
        List<Long> guardianIds = parentProfileRepository.findAllByInviteCodeIn(
                List.of(parentProfile.getInviteCode()))
                .stream()
                .map(pp -> pp.getGuardian().getId())
                .distinct()
                .collect(Collectors.toList());

        // 조회한 게시물 수 vs 전체 게시물 수 비교
        long viewedCount = albumViewRepository.countViewedAlbumsByMember(event.memberId());
        long totalCount = albumViewRepository.countTotalAlbumsByGuardians(guardianIds);

        if (viewedCount == totalCount && totalCount > 0) {
            // 모든 보호자들에게 알림 발송
            String inviteCode = parentProfile.getInviteCode();
            List<ParentProfile> allParentProfiles = parentProfileRepository.findAllByInviteCodeIn(List.of(inviteCode));

            for (ParentProfile profile : allParentProfiles) {
                FcmSendDto dto = new FcmSendDto(
                        parentMember.getName() + "님이 모든 소식을 확인했어요!",
                        "새로운 소식을 공유해보세요.",
                        FcmCategory.ALBUM,
                        ""
                );
                fcmService.sendMessageToUser(profile.getGuardian().getId(), dto);
            }
        }
    }
}
