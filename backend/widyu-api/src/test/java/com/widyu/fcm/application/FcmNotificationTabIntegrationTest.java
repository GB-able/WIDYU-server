package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.FcmNotification;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.dto.response.FcmCategoryResponse;
import com.widyu.fcm.dto.response.FcmNotificationResponse;
import com.widyu.fcm.repository.FcmNotificationRepository;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, FcmService.class})
class FcmNotificationTabIntegrationTest {
    @Autowired private FcmService service;
    @Autowired private FcmNotificationRepository notifications;
    @Autowired private MemberRepository members;
    @MockBean private MemberUtil memberUtil;
    @MockBean private JPAQueryFactory queryFactory;
    @MockBean private FcmDeliveryProperties deliveryProperties;
    @MockBean private FirebaseProperties firebaseProperties;
    @MockBean private FcmOutboxService outboxService;
    @MockBean private FcmTransport transport;
    @MockBean private FamilyAccessService familyAccessService;

    @Test
    @DisplayName("보호자가 알림 탭을 조회하면 전체부터 기타까지 7개를 순서대로 반환한다")
    void 보호자가_알림_탭을_조회하면_7개를_순서대로_반환한다() {
        // given
        Member guardian = member(MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        notification(guardian, NotificationType.HEART_RATE_EMERGENCY, FcmCategory.HEART_MESSAGE, null);
        notification(guardian, NotificationType.ALBUM_CREATED, FcmCategory.ALBUM, null);
        notification(guardian, NotificationType.ALBUM_CREATED, FcmCategory.ALBUM, LocalDateTime.now().minusDays(1));

        // when
        List<FcmCategoryResponse> tabs = service.getNotificationCategories();

        // then
        assertThat(tabs).extracting(FcmCategoryResponse::getLabel)
                .containsExactly("ALL", "UNREAD", "MESSAGE", "GOAL", "ALBUM", "LOCATION", "ETC");
        assertThat(tabs).extracting(FcmCategoryResponse::getName)
                .containsExactly("전체", "안 읽음", "메시지", "목표", "앨범", "위치", "기타");
        assertThat(tabs).extracting(FcmCategoryResponse::getCount)
                .containsExactly(2L, 2L, 0L, 0L, 1L, 1L, 0L);
    }

    @Test
    @DisplayName("시니어가 알림 탭을 조회하면 위치를 뺀 6개를 반환하고 위치 목록은 거절한다")
    void 시니어가_알림_탭을_조회하면_위치를_뺀다() {
        // given
        Member senior = member(MemberType.SENIOR);
        given(memberUtil.getCurrentMember()).willReturn(senior);

        // when
        List<FcmCategoryResponse> tabs = service.getNotificationCategories();

        // then
        assertThat(tabs).extracting(FcmCategoryResponse::getLabel)
                .containsExactly("ALL", "UNREAD", "MESSAGE", "GOAL", "ALBUM", "ETC");
        assertThatThrownBy(() -> service.getNotificationsForCurrentUser("LOCATION", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NOTIFICATION_CENTER_FILTER);
        assertThatThrownBy(() -> service.getNotificationsForCurrentUser("TARGET", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_NOTIFICATION_CENTER_FILTER);
    }

    @Test
    @DisplayName("탭별 목록을 조회하면 목표는 통합하고 위급 알림은 위치, 포인트와 옛 기타 행은 기타에 넣는다")
    void 탭별_목록을_조회하면_분류_기준대로_나눈다() {
        // given
        Member guardian = member(MemberType.GUARDIAN);
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        FcmNotification medicine = notification(guardian, NotificationType.MEDICATION_PROOF_MISSING,
                FcmCategory.MEDICINE_SCHEDULE, null);
        FcmNotification walk = notification(guardian, NotificationType.WALK_GOAL_CHANGED, FcmCategory.WALK, null);
        FcmNotification goal = notification(guardian, NotificationType.GOAL_ACHIEVED, FcmCategory.TARGET, null);
        FcmNotification emergency = notification(guardian, NotificationType.HEART_RATE_EMERGENCY,
                FcmCategory.HEART_MESSAGE, null);
        FcmNotification heart = notification(guardian, NotificationType.HEART_MESSAGE_RECEIVED,
                FcmCategory.HEART_MESSAGE, null);
        FcmNotification point = notification(guardian, NotificationType.POINT_EARNED, FcmCategory.TARGET, null);
        FcmNotification legacyEtc = notification(guardian, null, FcmCategory.ETC, null);
        FcmNotification legacyNotice = notification(guardian, null, FcmCategory.LOCATION_NOTICE, null);
        notification(guardian, NotificationType.GOAL_ACHIEVED, FcmCategory.TARGET, LocalDateTime.now().minusDays(1));

        // when / then
        assertThat(ids("GOAL")).containsExactly(goal.getId(), walk.getId(), medicine.getId());
        assertThat(ids("LOCATION")).containsExactly(emergency.getId());
        assertThat(ids("MESSAGE")).containsExactly(heart.getId());
        assertThat(ids("ETC")).containsExactly(legacyNotice.getId(), legacyEtc.getId(), point.getId());
        assertThat(ids("ALL")).hasSize(8);
        assertThat(ids("UNREAD")).hasSize(8);
    }

    private List<Long> ids(String tab) {
        return service.getNotificationsForCurrentUser(tab, null).notifications().stream()
                .map(FcmNotificationResponse::notificationId)
                .toList();
    }

    private Member member(MemberType type) {
        return members.saveAndFlush(Member.createMember(type, "회원", "01012345678"));
    }

    private FcmNotification notification(Member member, NotificationType type,
                                         FcmCategory category, LocalDateTime expiresAt) {
        String eventId = null;
        if (type != null) {
            eventId = UUID.randomUUID().toString();
        }
        return notifications.saveAndFlush(FcmNotification.builder()
                .recipientMember(member)
                .eventId(eventId)
                .type(type)
                .fcmCategory(category)
                .title("알림 제목")
                .body("알림 본문")
                .isRead(false)
                .expiresAt(expiresAt)
                .build());
    }
}
