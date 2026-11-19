package com.widyu.fcm.application;

import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.dto.NotificationCopy;
import com.widyu.fcm.dto.request.SendNotificationRequest;
import com.widyu.fcm.dto.response.FcmCategoryResponse;
import com.widyu.fcm.dto.response.FcmNotificationResponses;
import com.widyu.fcm.dto.response.ToastResDto;
import com.widyu.fcm.FcmNotification;
import com.widyu.fcm.MemberFcmToken;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.repository.FcmNotificationRepository;
import com.widyu.fcm.repository.MemberFcmTokenRepository;
import com.widyu.album.repository.AlbumViewRepository;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.member.FamilyMembership;
import com.widyu.member.SeniorProfile;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.notification.NotificationCenterFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmService {

    private final FcmDeliveryProperties deliveryProperties;
    private final FirebaseProperties firebaseProperties;
    private final FcmOutboxService outboxService;
    private final FcmTransport transport;
    private final FcmNotificationRepository fcmNotificationRepository;
    private final MemberFcmTokenRepository memberFcmTokenRepository;
    private final AlbumViewRepository albumViewRepository;
    private final FamilyMembershipRepository familyMembershipRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final MemberUtil memberUtil;
    private final FamilyAccessService familyAccessService;

    // 카테고리 및 커서 기반 알림 목록 조회
    public FcmNotificationResponses getNotificationsForCurrentUser(String category, Long cursor) {
        Member member = memberUtil.getCurrentMember();

        int pageSize = 10;
        int fetchSize = pageSize + 1;
        Pageable pageable = PageRequest.of(0, fetchSize);

        NotificationCenterFilter tab = NotificationCenterFilter.parse(category, member.getType());
        LocalDateTime now = LocalDateTime.now();
        List<FcmNotification> notifications;
        if (tab.isCategory()) {
            notifications = fcmNotificationRepository.findCenterPageByCategory(
                    member.getId(), now, cursor, tab.types(), tab.legacyCategories(), pageable);
        } else {
            notifications = fcmNotificationRepository.findCenterPage(
                    member.getId(), now, cursor, tab == NotificationCenterFilter.UNREAD, pageable);
        }

        if (notifications.isEmpty()) {
            return FcmNotificationResponses.empty();
        }

        boolean hasNext = notifications.size() > pageSize;
        List<FcmNotification> pageNotifications = notifications;
        Long nextCursor = null;
        if (hasNext) {
            pageNotifications = notifications.subList(0, pageSize);
            nextCursor = pageNotifications.get(pageSize - 1).getId();
        }

        return FcmNotificationResponses.of(pageNotifications, hasNext, nextCursor);
    }

    // 알림 개별 읽기
    @Transactional
    public String markAsRead(Long notificationId) {
        Member member = memberUtil.getCurrentMember();
        FcmNotification notification = fcmNotificationRepository
                .findByIdAndMemberFcmToken_MemberId(notificationId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FCM_NOTIFICATION_NOT_FOUND));

        if (!notification.isRead()) {
            notification.markAsRead();
            return "알림 읽음 처리 성공";
        } else {
            return "이미 읽은 알림입니다";
        }
    }

    @Transactional
    public void sendMessageToUser(Long memberId, FcmSendDto fcmSendDto) {
        outboxService.enqueue(memberId, fcmSendDto);
    }

    /** 관리자 테스트 발송. 알림 설정과 알림센터를 거치지 않고 활성 토큰마다 FCM을 직접 호출한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<FcmTestAttempt> sendTestMessageToUser(Long memberId, FcmSendDto fcmSendDto) {
        List<FcmTestAttempt> attempts = new ArrayList<>();
        long deadline = System.nanoTime() + deliveryProperties.adminTimeout().toNanos();
        List<MemberFcmToken> tokens = memberFcmTokenRepository.findAllByMemberIdAndActiveTrue(memberId);
        for (MemberFcmToken tokenEntity : tokens) {
            if (deadline - System.nanoTime() < firebaseProperties.http().totalTimeout().toNanos()) {
                attempts.add(FcmTestAttempt.timeBudgetExhausted(tokenEntity.getId()));
                continue;
            }
            FcmTransport.Result result = transport.send(tokenEntity.getToken(), fcmSendDto,
                    () -> memberFcmTokenRepository.isDeliverable(tokenEntity.getId(), memberId));
            attempts.add(FcmTestAttempt.of(tokenEntity.getId(), result));
        }
        return attempts;
    }

    public List<FcmCategoryResponse> getNotificationCategories() {
        Member member = memberUtil.getCurrentMember();

        LocalDateTime now = LocalDateTime.now();

        return NotificationCenterFilter.tabs(member.getType()).stream()
                .map(tab -> FcmCategoryResponse.of(tab, countUnread(member.getId(), tab, now)))
                .toList();
    }

    private long countUnread(Long memberId, NotificationCenterFilter tab, LocalDateTime now) {
        if (tab.isCategory()) {
            return fcmNotificationRepository.countCenterUnreadByCategory(
                    memberId, now, tab.types(), tab.legacyCategories());
        }
        return fcmNotificationRepository.countCenterUnread(memberId, now);
    }

    // 토스트 모달 알림
    public ToastResDto getToastNotification() {
        Member member = memberUtil.getCurrentMember();

        FamilyMembership myMembership = familyMembershipRepository.findByGuardianId(member.getId())
                .orElse(null);
        if (myMembership == null) {
            return null;
        }

        List<SeniorProfile> seniors = seniorProfileRepository
                .findAllByFamilyIdWithMember(myMembership.getFamily().getId());
        if (seniors.isEmpty()) {
            return null;
        }

        int[] thresholds = {0, 2, 4, 6};

        for (int threshold : thresholds) {
            for (SeniorProfile senior : seniors) {
                long unviewedCount = calculateUnviewedCount(member.getId(), senior);

                if ((threshold == 0 && unviewedCount == 0) ||
                    (threshold > 0 && unviewedCount > 0 && unviewedCount <= threshold)) {
                    return createToastMessage(senior.getMember().getName(), unviewedCount);
                }
            }
        }

        return null;
    }

    private long calculateUnviewedCount(Long guardianId, SeniorProfile senior) {
        Long seniorMemberId = senior.getMember().getId();
        long totalCount = albumViewRepository.countTotalAlbumsByParent(guardianId);
        long viewedCount = albumViewRepository.countViewedAlbumsByGuardianAndParent(seniorMemberId, guardianId);

        return totalCount - viewedCount;
    }

    private ToastResDto createToastMessage(String seniorName, long unviewedCount) {
        if (unviewedCount == 0) {
            // 같은 순간 가는 모두 확인 푸시(A07)와 같은 문구를 쓴다(위듀케어 알림 정책서 10절).
            NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_ALL_VIEWED, "A07",
                    Map.of("시니어 이름", seniorName));
            return ToastResDto.of(copy.title(), copy.body());
        }
            return ToastResDto.from(seniorName + "님께서 보실 소식이 " + unviewedCount + "개밖에 남지 않았어요.");
    }

    // 응원 알림 보내기. 잠금화면에는 고정 문구, 앱 안 문구와 알림센터에는 40자 미리보기만 싣는다(LLD-0167).
    @Transactional
    public void sendNotificationToMember(SendNotificationRequest sendNotificationRequest) {
        Member sender = memberUtil.getCurrentMember();
        // 없는 회원도 다른 가족과 같은 403으로 답해 회원 id 존재 여부를 드러내지 않는다
        if (!familyAccessService.getFamilyMemberIds(sender).contains(sendNotificationRequest.receiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "같은 가족에게만 응원 메시지를 보낼 수 있습니다.");
        }
        sendMessageToUser(sendNotificationRequest.receiverId(), FcmSendDto.ofMessage(
                NotificationType.CHEER_MESSAGE_RECEIVED, sender, sendNotificationRequest.content()));
    }
}
