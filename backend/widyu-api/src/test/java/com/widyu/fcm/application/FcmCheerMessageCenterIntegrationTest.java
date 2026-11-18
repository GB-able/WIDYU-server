package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.FcmNotification;
import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.MemberFcmToken;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.repository.FcmOutboxRepository;
import com.widyu.fcm.repository.MemberFcmTokenRepository;
import com.widyu.fcm.dto.request.SendNotificationRequest;
import com.widyu.fcm.repository.FcmNotificationRepository;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.notification.application.NotificationCenterService;
import com.widyu.notification.dto.response.NotificationCenterResponse;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@EnableConfigurationProperties({FcmDeliveryProperties.class, FirebaseProperties.class})
@Import({JpaAuditingConfig.class, FcmService.class, FcmOutboxService.class, FcmEligibility.class,
        NotificationSettingService.class, NotificationCenterService.class, FamilyAccessService.class})
class FcmCheerMessageCenterIntegrationTest {
    @Autowired private FcmService fcmService;
    @Autowired private NotificationCenterService notificationCenterService;
    @Autowired private FcmNotificationRepository notifications;
    @Autowired private FcmOutboxRepository outbox;
    @Autowired private MemberFcmTokenRepository tokens;
    @Autowired private MemberRepository members;
    @Autowired private FamilyRepository families;
    @Autowired private FamilyMembershipRepository memberships;
    @Autowired private SeniorProfileRepository seniorProfiles;
    @MockBean private MemberUtil memberUtil;
    @MockBean private FcmOutboxDispatcher dispatcher;
    @MockBean private FcmTransport transport;

    @Test
    @DisplayName("가족에게 응원 메시지를 보내면 알림센터와 재시도 큐에 원문 없이 미리보기와 고정 문구만 저장한다")
    void 가족에게_응원_메시지를_보내면_원문_없이_저장한다() {
        // given
        Member senior = members.save(Member.createMember(MemberType.SENIOR, "어르신", "01011112222"));
        Member guardian = members.save(Member.createMember(MemberType.GUARDIAN, "보호자", "01033334444"));
        Family family = families.save(Family.createFamily("CHR001"));
        seniorProfiles.save(SeniorProfile.createSeniorProfile(senior, family, "서울", "CHRINV1",
                LocalDate.of(1950, 1, 1)));
        memberships.save(FamilyMembership.createMembership(family, guardian));
        tokens.save(MemberFcmToken.builder().member(senior).token("senior-token").active(true).build());
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        String message = "고유원문표식 " + "나".repeat(40);
        String preview = "고유원문표식 " + "나".repeat(32) + "…";

        // when
        fcmService.sendNotificationToMember(new SendNotificationRequest(senior.getId(), message));
        given(memberUtil.getCurrentMember()).willReturn(senior);
        NotificationCenterResponse messageTab = notificationCenterService.list("MESSAGE", null);

        // then
        FcmNotification center = notifications.findAll().getFirst();
        assertThat(center.getType()).isEqualTo(NotificationType.CHEER_MESSAGE_RECEIVED);
        assertThat(center.getFcmCategory()).isEqualTo(FcmCategory.HEART_MESSAGE);
        assertThat(center.getTitle()).isEqualTo("보호자 님이 응원메시지를 보냈어요!");
        assertThat(center.getBody()).isEqualTo(preview);
        assertThat(center.getActorDisplayName()).isEqualTo("보호자");
        assertThat(Duration.between(center.getCreatedAt(), center.getExpiresAt()))
                .isBetween(Duration.ofDays(90).minusMinutes(1), Duration.ofDays(90).plusMinutes(1));
        FcmOutbox pushed = outbox.findAll().getFirst();
        assertThat(pushed.getTitle()).isEqualTo("보호자 님이 응원메시지를 보냈어요!");
        assertThat(pushed.getBody()).isEqualTo("앱에서 응원메시지를 확인해주세요.");
        assertThat(pushed.getTitle() + pushed.getBody() + pushed.getDataPayload()).doesNotContain(message);
        assertThat(pushed.getTitle() + pushed.getBody()).doesNotContain("고유원문표식");
        FcmDelivery delivery = FcmDelivery.from(pushed);
        assertThat(delivery.message().data()).containsEntry("inAppBody", preview);
        assertThat(delivery.notificationId()).isEqualTo(center.getId());
        assertThat(messageTab.items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo("CHEER_MESSAGE_RECEIVED");
            assertThat(item.body()).isEqualTo(preview);
        });
        assertThat(messageTab.unreadCounts())
                .containsEntry("MESSAGE", 1)
                .containsEntry("ETC", 0);
    }
}
