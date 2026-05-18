package com.widyu.fcm.event.album.listener;

import com.widyu.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.fcm.event.album.dto.AlbumCommentedEvent;
import com.widyu.fcm.event.album.dto.AlbumLikedEvent;
import com.widyu.fcm.event.album.dto.AlbumUnlockedEvent;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.album.Album;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.global.entity.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlbumNotificationListener {

    private static final String ALBUM_DEFAULT_IMAGE = "album.png";

    private final FcmService fcmService;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberRepository memberRepository;
    private final AlbumViewRepository albumViewRepository;
    private final AlbumRepository albumRepository;

    @EventListener
    public void handleAlbumCreated(AlbumCreatedEvent event) {
        Member author = memberRepository.findById(event.authorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        String title = author.getName() + "님이 새로운 소식을 전했어요!";
        String content = "새로운 앨범을 확인해보세요.";

        sendNotificationToFamilyMembers(event.authorId(), title, content, author.getProfileImage());
    }

    @EventListener
    @Transactional
    public void handleAlbumViewed(AlbumViewedEvent event) {
        Member viewer = memberRepository.findById(event.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        Album album = albumRepository.findByIdAndStatusWithCollections(event.albumId(), Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

        if (hasViewedAllAlbums(event.memberId(), album.getId())) {
            Member albumWriter = album.getMember();
            String title = viewer.getName() + "님이 " + albumWriter.getName() + "님의 모든 소식을 확인했어요!";
            String content = "새로운 소식을 공유해보세요.";
            sendNotificationToSpecificMember(albumWriter.getId(), title, content);
        }
    }

    private void sendNotificationToFamilyMembers(Long memberId, String title, String content, String image) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        if (member.getSeniorProfile() != null) {
            Long familyId = member.getSeniorProfile().getFamily().getId();
            List<FamilyMembership> memberships = familyMembershipRepository.findAllByFamilyIdWithGuardian(familyId);
            for (FamilyMembership membership : memberships) {
                FcmSendDto dto = new FcmSendDto(title, content, FcmCategory.ALBUM, "", image);
                fcmService.sendMessageToUser(membership.getGuardian().getId(), dto);
            }
        } else {
            familyMembershipRepository.findByGuardianId(memberId).ifPresent(myMembership -> {
                List<SeniorProfile> seniors = seniorProfileRepository
                        .findAllByFamilyIdWithMember(myMembership.getFamily().getId());
                for (SeniorProfile senior : seniors) {
                    FcmSendDto dto = new FcmSendDto(title, content, FcmCategory.ALBUM, "", image);
                    fcmService.sendMessageToUser(senior.getMember().getId(), dto);
                }
            });
        }
    }

    private void sendNotificationToSpecificMember(Long memberId, String title, String content) {
        FcmSendDto dto = new FcmSendDto(title, content, FcmCategory.ALBUM, "", ALBUM_DEFAULT_IMAGE);
        fcmService.sendMessageToUser(memberId, dto);
    }

    private boolean hasViewedAllAlbums(Long viewerId, Long albumId) {
        Member writer = albumRepository.findById(albumId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND))
                .getMember();

        long totalCount = albumRepository.countByMemberId(writer.getId());
        long viewedCount = albumViewRepository.countViewedAlbumsByGuardianAndParent(viewerId, writer.getId());

        log.info("작성자: {}, 전체: {}, 본 개수: {}", writer.getName(), totalCount, viewedCount);
        return viewedCount == totalCount && totalCount > 0;
    }

    @Scheduled(cron = "0 0 10 * * *")
    @Transactional
    public void checkInactiveUsersAndSendNotification() {
        LocalDateTime now = LocalDateTime.now();
        checkAndNotifyInactiveUsers(3, now);
        checkAndNotifyInactiveUsers(5, now);
        checkAndNotifyInactiveUsers(7, now);
    }

    private void checkAndNotifyInactiveUsers(int days, LocalDateTime now) {
        LocalDateTime cutoffDate = now.minusDays(days);
        List<Member> allMembers = memberRepository.findAll();

        for (Member member : allMembers) {
            Optional<LocalDateTime> lastUploadDate = albumRepository.findLastUploadDateByMember(member, Status.ACTIVE);

            boolean shouldNotify = false;
            if (lastUploadDate.isEmpty()) {
                if (member.getCreatedAt().isBefore(cutoffDate)) {
                    shouldNotify = true;
                }
            } else {
                if (lastUploadDate.get().isBefore(cutoffDate)) {
                    shouldNotify = true;
                }
            }

            if (shouldNotify) {
                sendInactivityNotificationToSeniors(member, days);
            }
        }
    }

    private void sendInactivityNotificationToSeniors(Member member, int days) {
        FamilyMembership myMembership = familyMembershipRepository.findByGuardianId(member.getId())
                .orElse(null);
        if (myMembership == null) {
            return;
        }

        List<SeniorProfile> seniors = seniorProfileRepository
                .findAllByFamilyIdWithMember(myMembership.getFamily().getId());
        if (seniors.isEmpty()) {
            return;
        }

        String message = member.getName() + "님, " + days + "일 간 소식이 뜸했어요. 새로운 근황을 전하는 건 어떨까요?";

        for (SeniorProfile senior : seniors) {
            FcmSendDto dto = new FcmSendDto(message, "새로운 소식을 공유해보세요.", FcmCategory.ALBUM, "", ALBUM_DEFAULT_IMAGE);
            try {
                fcmService.sendMessageToUser(senior.getMember().getId(), dto);
                log.info("{}일 비활성 알림 전송 완료: {} -> {}", days, member.getName(), senior.getMember().getName());
            } catch (Exception e) {
                log.error("{}일 비활성 알림 전송 실패: {} -> {}", days, member.getName(), senior.getMember().getName(), e);
            }
        }
    }

    @EventListener
    public void handleAlbumCommented(AlbumCommentedEvent event) {
        Member commenter = memberRepository.findById(event.commenterMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        Member albumAuthor = memberRepository.findById(event.albumAuthorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        if (event.commenterMemberId().equals(event.albumAuthorId())) {
            return;
        }

        FcmSendDto dto = new FcmSendDto(
                commenter.getName() + "님이 회원님의 게시물에 댓글을 남겼어요!",
                "답글을 달아주세요.",
                FcmCategory.ALBUM,
                "",
                commenter.getProfileImage()
        );
        fcmService.sendMessageToUser(albumAuthor.getId(), dto);
    }

    @EventListener
    public void handleAlbumLiked(AlbumLikedEvent event) {
        Member liker = memberRepository.findById(event.likerMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        Member albumAuthor = memberRepository.findById(event.albumAuthorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        if (event.likerMemberId().equals(event.albumAuthorId())) {
            return;
        }

        FcmSendDto dto = new FcmSendDto(
                liker.getName() + "님이 회원님의 게시물을 좋아합니다!",
                "게시물을 확인해보세요.",
                FcmCategory.ALBUM,
                "",
                liker.getProfileImage()
        );
        fcmService.sendMessageToUser(albumAuthor.getId(), dto);
    }

    @EventListener
    public void handleAlbumUnlocked(AlbumUnlockedEvent event) {
        Member parentMember = memberRepository.findById(event.parentMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_PARENT_MEMBER_NOT_FOUND));

        Album album = albumRepository.findById(event.albumId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

        FcmSendDto dto = new FcmSendDto(
                parentMember.getName() + "님이 회원님의 게시물을 잠금해제했어요.",
                "새로운 소식을 확인해보세요.",
                FcmCategory.ALBUM,
                "",
                parentMember.getProfileImage()
        );
        fcmService.sendMessageToUser(album.getMember().getId(), dto);
    }
}
