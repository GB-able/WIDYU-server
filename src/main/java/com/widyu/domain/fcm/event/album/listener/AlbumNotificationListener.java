package com.widyu.domain.fcm.event.album.listener;

import com.widyu.domain.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.domain.fcm.event.album.dto.AlbumCommentedEvent;
import com.widyu.domain.fcm.event.album.dto.AlbumLikedEvent;
import com.widyu.domain.fcm.event.album.dto.AlbumUnlockedEvent;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.repository.AlbumViewRepository;
import com.widyu.domain.album.repository.AlbumRepository;
import com.widyu.domain.fcm.application.FcmService;
import com.widyu.domain.fcm.api.dto.FcmSendDto;
import com.widyu.domain.fcm.domain.FcmCategory;
import com.widyu.domain.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.domain.member.entity.Member;
import com.widyu.domain.member.entity.ParentProfile;
import com.widyu.domain.member.repository.MemberRepository;
import com.widyu.domain.member.repository.ParentProfileRepository;
import com.widyu.global.domain.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlbumNotificationListener {

    private final FcmService fcmService;
    private final ParentProfileRepository parentProfileRepository;
    private final MemberRepository memberRepository;
    private final AlbumViewRepository albumViewRepository;
    private final AlbumRepository albumRepository;

    // 게시물 작성시 알림 발송 (작성자가 부모님이면 보호자들에게, 보호자면 부모님들에게)
    @Async
    @EventListener
    public void handleAlbumCreated(AlbumCreatedEvent event) {
        Member author = memberRepository.findById(event.authorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        String title = author.getName() + "님이 새로운 소식을 전했어요!";
        String content = "새로운 앨범을 확인해보세요.";

        sendNotificationToFamilyMembers(event.authorId(), title, content);
    }

    // 게시물 조회시 해당 작성자의 모든 게시물 확인했는지 체크 및 알림 발송
    @Async
    @EventListener
    @Transactional
    public void handleAlbumViewed(AlbumViewedEvent event) {
        Member viewer = memberRepository.findById(event.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        // 조회한 앨범의 작성자 찾기 (Fetch Join으로 Member도 함께 조회)
        Album album = albumRepository.findByIdAndStatusWithCollections(event.albumId(), Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));

        // 해당 작성자의 모든 게시물을 확인했는지 체크
        if (hasViewedAllAlbums(event.memberId(), album.getId())) {
            Member albumWriter = album.getMember();
            String title = viewer.getName() + "님이 " + albumWriter.getName() + "님의 모든 소식을 확인했어요!";
            String content = "새로운 소식을 공유해보세요.";

            // 앨범 작성자에게 알림 발송
            sendNotificationToSpecificMember(albumWriter.getId(), title, content);
        }
    }

    // 가족 구성원들에게 알림 발송하는 공통 메서드
    private void sendNotificationToFamilyMembers(Long memberId, String title, String content) {
        // 사용자가 부모님인지 보호자인지 확인
        List<ParentProfile> memberAsParent = parentProfileRepository.findAll()
                .stream()
                .filter(pp -> pp.getMember().getId().equals(memberId))
                .toList();

        if (!memberAsParent.isEmpty()) {
            // 부모님인 경우 → 보호자들에게 알림 발송
            for (ParentProfile profile : memberAsParent) {
                FcmSendDto dto = new FcmSendDto(title, content, FcmCategory.ALBUM, "");
                fcmService.sendMessageToUser(profile.getGuardian().getId(), dto);
            }
        } else {
            // 보호자인 경우 → 부모님들에게 알림 발송
            List<ParentProfile> parentProfiles = parentProfileRepository.findAllByGuardianId(memberId);

            for (ParentProfile parentProfile : parentProfiles) {
                FcmSendDto dto = new FcmSendDto(title, content, FcmCategory.ALBUM, "");
                fcmService.sendMessageToUser(parentProfile.getMember().getId(), dto);
            }
        }
    }

    // 특정 멤버에게 알림 발송하는 메서드
    private void sendNotificationToSpecificMember(Long memberId, String title, String content) {
        FcmSendDto dto = new FcmSendDto(title, content, FcmCategory.ALBUM, "");
        fcmService.sendMessageToUser(memberId, dto);
    }

    // 특정 작성자의 모든 게시물을 확인했는지 체크하는 메서드
    private boolean hasViewedAllAlbums(Long viewerId, Long albumId) {
        // 내가 특정 작성자의 게시물을 본 개수
        long viewedCount = albumViewRepository.countViewedAlbumsByMemberIdAndAlbumId(viewerId, albumId);

        Member writer = albumRepository.findById(albumId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND))
                .getMember();

        // 그 작성자가 쓴 총 게시물 수
        long totalCount = albumRepository.countByMemberId(writer.getId());
        log.info(String.valueOf(totalCount) +" " + String.valueOf(viewedCount));
        return viewedCount == totalCount && totalCount > 0;
    }

    // 3/5/7일 비활성 사용자 체크 및 알림 발송
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
                LocalDateTime lastUpload = lastUploadDate.get();
                if (lastUpload.isBefore(cutoffDate)) {
                    shouldNotify = true;
                }
            }

            if (shouldNotify) {
                sendInactivityNotificationToParents(member, days);
            }
        }
    }

    private void sendInactivityNotificationToParents(Member member, int days) {
        List<ParentProfile> parentProfiles = parentProfileRepository.findAllByGuardianId(member.getId());

        if (parentProfiles.isEmpty()) {
            return;
        }

        String message = member.getName() + "님, " + days + "일 간 소식이 뜸했어요. 새로운 근황을 전하는 건 어떨까요?";

        for (ParentProfile parentProfile : parentProfiles) {
            FcmSendDto dto = new FcmSendDto(
                    message,
                    "새로운 소식을 공유해보세요.",
                    FcmCategory.ALBUM,
                    ""
            );
            try {
                fcmService.sendMessageToUser(parentProfile.getMember().getId(), dto);
                log.info("{}일 비활성 알림 전송 완료: {} -> {}", days, member.getName(), parentProfile.getMember().getName());
            } catch (Exception e) {
                log.error("{}일 비활성 알림 전송 실패: {} -> {}", days, member.getName(), parentProfile.getMember().getName(), e);
            }
        }
    }

    // 게시글에 댓글이 달리면 게시물 주인에게 알림 발송
    @Async
    @EventListener
    public void handleAlbumCommented(AlbumCommentedEvent event) {
        Member commenter = memberRepository.findById(event.commenterMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        Member albumAuthor = memberRepository.findById(event.albumAuthorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        // 자신의 게시물에 자신이 댓글을 단 경우 알림 발송하지 않음
        if (event.commenterMemberId().equals(event.albumAuthorId())) {
            return;
        }

        FcmSendDto dto = new FcmSendDto(
                commenter.getName() + "님이 회원님의 게시물에 댓글을 남겼어요!",
                "답글을 달아주세요.",
                FcmCategory.ALBUM,
                ""
        );
        fcmService.sendMessageToUser(albumAuthor.getId(), dto);
    }

    // 게시글에 좋아요가 달리면 게시물 주인에게 알림 발송
    @Async
    @EventListener
    public void handleAlbumLiked(AlbumLikedEvent event) {
        Member liker = memberRepository.findById(event.likerMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        Member albumAuthor = memberRepository.findById(event.albumAuthorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));

        // 자신의 게시물에 자신이 좋아요를 누른 경우 알림 발송하지 않음
        if (event.likerMemberId().equals(event.albumAuthorId())) {
            return;
        }

        FcmSendDto dto = new FcmSendDto(
                liker.getName() + "님이 회원님의 게시물을 좋아합니다!",
                "게시물을 확인해보세요.",
                FcmCategory.ALBUM,
                ""
        );
        fcmService.sendMessageToUser(albumAuthor.getId(), dto);
    }

    // 부모님이 게시물을 잠금 해제했을 때 해당 앨범 작성자에게 알림 발송
    @Async
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
                ""
        );
        fcmService.sendMessageToUser(album.getMember().getId(), dto);
    }
}
