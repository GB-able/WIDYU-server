package com.widyu.fcm.event.album.listener;

import com.widyu.album.Album;
import com.widyu.album.repository.AlbumRepository;
import com.widyu.album.repository.AlbumUnlockRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.GuardianDeepLinks;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.fcm.event.album.dto.AlbumCommentedEvent;
import com.widyu.fcm.event.album.dto.AlbumCreatedEvent;
import com.widyu.fcm.event.album.dto.AlbumLikedEvent;
import com.widyu.fcm.event.album.dto.AlbumUnlockedEvent;
import com.widyu.fcm.event.album.dto.AlbumViewedEvent;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AlbumNotificationListener {

    private final FcmService fcmService;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberRepository memberRepository;
    private final AlbumViewRepository albumViewRepository;
    private final AlbumRepository albumRepository;
    private final AlbumUnlockRepository albumUnlockRepository;

    @EventListener
    @Transactional
    public void handleAlbumCreated(AlbumCreatedEvent event) {
        Member author = memberRepository.findById(event.authorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        String albumId = event.albumId().toString();
        NotificationCopy uploadCopy = NotificationCopy.of(NotificationType.ALBUM_UPLOAD_COMPLETE, "A01", Map.of());
        String uploadDeepLink = null;
        if (author.getType() == MemberType.GUARDIAN) {
            uploadDeepLink = GuardianDeepLinks.post(albumId);
        }
        // 같은 게시물의 업로드 완료가 다시 접수돼도 알림센터 행이 하나만 남게 게시물로 정한다.
        send(author.getId(), FcmSendDto.builder().title(uploadCopy.title()).content(uploadCopy.body())
                .fcmCategory(NotificationType.ALBUM_UPLOAD_COMPLETE.fcmCategory())
                .notificationType(NotificationType.ALBUM_UPLOAD_COMPLETE).eventId("A01:" + albumId)
                .entityId(albumId).deepLink(uploadDeepLink).image(author.getProfileImage()).build());

        if (author.getType() == MemberType.SENIOR) {
            Optional<Long> familyId = seniorProfileRepository.findFamilyIdByMemberId(author.getId());
            if (familyId.isEmpty()) {
                return;
            }
            notifyGuardians(author, familyId.get(), albumId, author.getId());
            return;
        }

        Optional<Long> familyId = familyMembershipRepository.findFamilyIdByGuardianId(author.getId());
        if (familyId.isEmpty()) {
            return;
        }
        List<SeniorProfile> seniors = seniorProfileRepository.findAllByFamilyIdWithMember(familyId.get());
        for (SeniorProfile senior : seniors) {
            NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_CREATED, "A02-S",
                    Map.of("작성자 이름", author.getName()));
            send(senior.getMember().getId(), FcmSendDto.of(NotificationType.ALBUM_CREATED, copy,
                    albumId, null, author.getId(), null, author.getProfileImage()));
        }
        // 정책서 A02-C는 「다른 가족이나 어르신이 올렸을 때」라 같은 가족의 다른 보호자에게도 보낸다.
        notifyGuardians(author, familyId.get(), albumId, null);
    }

    /** 가족 보호자에게 A02-C를 보낸다. 올린 사람이 보호자면 본인은 뺀다. */
    private void notifyGuardians(Member author, Long familyId, String albumId, Long seniorId) {
        for (FamilyMembership membership : familyMembershipRepository.findAllByFamilyIdWithGuardian(familyId)) {
            Member guardian = membership.getGuardian();
            if (guardian.getId().equals(author.getId())) {
                continue;
            }
            NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_CREATED, "A02-C",
                    Map.of("작성자 이름", author.getName()));
            send(guardian.getId(), FcmSendDto.of(NotificationType.ALBUM_CREATED, copy, albumId,
                    GuardianDeepLinks.post(albumId), author.getId(), seniorId, author.getProfileImage()));
        }
    }

    @EventListener
    @Transactional
    public void handleAlbumViewed(AlbumViewedEvent event) {
        Member viewer = memberRepository.findById(event.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        Album album = albumRepository.findByIdAndStatusWithCollections(event.albumId(), Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
        Member writer = album.getMember();
        if (writer.getType() != MemberType.GUARDIAN || viewer.getType() != MemberType.SENIOR) {
            return;
        }
        long total = albumViewRepository.countTotalAlbumsByParent(writer.getId());
        long viewed = albumViewRepository.countViewedAlbumsByGuardianAndParent(viewer.getId(), writer.getId());
        if (total == 0 || viewed != total) {
            return;
        }
        NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_ALL_VIEWED, "A07",
                Map.of("시니어 이름", viewer.getName()));
        send(writer.getId(), FcmSendDto.of(NotificationType.ALBUM_ALL_VIEWED, copy, null,
                null, viewer.getId(), viewer.getId(), viewer.getProfileImage()));
    }

    @EventListener
    @Transactional
    public void handleAlbumCommented(AlbumCommentedEvent event) {
        Member commenter = memberRepository.findById(event.commenterMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        if (event.parentCommentId() == null) {
            notifyComment(event.albumAuthorId(), commenter, NotificationType.ALBUM_COMMENTED, "A03", event);
            return;
        }
        // A04는 원댓글 작성자와 게시물 작성자(알림 문구표 A04 수신자)에게 보낸다. 같은 사람이면 한 번만 보낸다.
        Set<Long> recipientIds = new LinkedHashSet<>(List.of(event.parentCommentAuthorId(), event.albumAuthorId()));
        for (Long recipientId : recipientIds) {
            notifyComment(recipientId, commenter, NotificationType.ALBUM_REPLIED, "A04", event);
        }
    }

    private void notifyComment(Long recipientId, Member commenter, NotificationType type, String code,
            AlbumCommentedEvent event) {
        if (commenter.getId().equals(recipientId)) {
            return;
        }
        Member recipient = memberRepository.findById(recipientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        NotificationCopy copy = NotificationCopy.of(type, code, Map.of("작성자 이름", commenter.getName()));
        String albumId = event.albumId().toString();
        String commentId = event.commentId().toString();
        String deepLink = null;
        Long seniorId = null;
        if (recipient.getType() == MemberType.GUARDIAN) {
            deepLink = GuardianDeepLinks.postComment(albumId, commentId);
            if (commenter.getType() == MemberType.SENIOR) {
                seniorId = commenter.getId();
            }
        }
        FcmSendDto dto = FcmSendDto.of(type, copy, albumId, deepLink,
                commenter.getId(), seniorId, commenter.getProfileImage())
                .withData(Map.of("commentId", commentId));
        send(recipient.getId(), dto);
    }

    @EventListener
    @Transactional
    public void handleAlbumLiked(AlbumLikedEvent event) {
        Member liker = memberRepository.findById(event.likerMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        Member writer = memberRepository.findById(event.albumAuthorId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_MEMBER_NOT_FOUND));
        if (liker.getId().equals(writer.getId())) {
            return;
        }
        NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_LIKED, "A05",
                Map.of("누른 사람 이름", liker.getName()));
        String albumId = event.albumId().toString();
        String deepLink = null;
        if (writer.getType() == MemberType.GUARDIAN) {
            deepLink = GuardianDeepLinks.post(albumId);
        }
        send(writer.getId(), FcmSendDto.of(NotificationType.ALBUM_LIKED, copy, albumId,
                deepLink, liker.getId(), null, liker.getProfileImage()));
    }

    @EventListener
    @Transactional
    public void handleAlbumUnlocked(AlbumUnlockedEvent event) {
        Member senior = memberRepository.findById(event.parentMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_PARENT_MEMBER_NOT_FOUND));
        Album album = albumRepository.findByIdWithMember(event.albumId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ALBUM_NOT_FOUND));
        Member writer = album.getMember();
        int remaining = Math.toIntExact(albumUnlockRepository.countRemainingLockedByWriterAndSenior(
                writer.getId(), senior.getId(), album.getId()));
        // 마지막 잠긴 게시물을 열었고 이것만 보면 모두 본 상태면, 이어지는 첫 조회에서 A07이 가므로 A06-Z는 보내지 않는다
        // (위듀케어 정책서 10절 추천). 해제 전 게시물은 조회로 치지 않아 이 게시물은 아직 조회 수에 없다.
        if (remaining > 0 || !allViewedAfterThis(senior, writer)) {
            String code = "A06-L";
            if (remaining == 0) {
                code = "A06-Z";
            }
            NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_UNLOCKED, code,
                    unlockCopyValues(senior.getName(), remaining));
            send(writer.getId(), FcmSendDto.of(NotificationType.ALBUM_UNLOCKED, copy,
                    album.getId().toString(), null,
                    senior.getId(), senior.getId(), senior.getProfileImage())
                    .withSeniorUnlockDetails(senior.getName(), remaining));
        }

        // 연 어르신 본인에게 쓴 포인트를 알린다(A08). 해제는 게시물마다 한 번이라 다시 접수돼도 센터 행은 하나다.
        NotificationCopy openedCopy = NotificationCopy.of(NotificationType.ALBUM_OPENED_BY_SENIOR, "A08",
                Map.of("포인트", Long.toString(Album.UNLOCK_PRICE)));
        send(senior.getId(), FcmSendDto.builder().title(openedCopy.title()).content(openedCopy.body())
                .fcmCategory(NotificationType.ALBUM_OPENED_BY_SENIOR.fcmCategory())
                .notificationType(NotificationType.ALBUM_OPENED_BY_SENIOR)
                .eventId("A08:" + album.getId() + ":" + senior.getId())
                .entityId(album.getId().toString()).image(writer.getProfileImage()).build());
    }

    private boolean allViewedAfterThis(Member senior, Member writer) {
        long viewed = albumViewRepository.countViewedAlbumsByGuardianAndParent(senior.getId(), writer.getId());
        return viewed + 1 == albumViewRepository.countTotalAlbumsByParent(writer.getId());
    }

    private Map<String, String> unlockCopyValues(String seniorName, int remaining) {
        if (seniorName == null || seniorName.isBlank()) {
            return Map.of("남은 개수", Integer.toString(remaining));
        }
        return Map.of("시니어 이름", seniorName, "남은 개수", Integer.toString(remaining));
    }

    private void send(Long recipientId, FcmSendDto dto) {
        fcmService.sendMessageToUser(recipientId, dto);
    }
}
