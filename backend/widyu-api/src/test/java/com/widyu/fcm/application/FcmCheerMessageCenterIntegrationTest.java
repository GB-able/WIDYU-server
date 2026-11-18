package com.widyu.fcm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.fcm.FcmCategory;
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
    @Autowired private MemberRepository members;
    @Autowired private FamilyRepository families;
    @Autowired private FamilyMembershipRepository memberships;
    @Autowired private SeniorProfileRepository seniorProfiles;
    @MockBean private MemberUtil memberUtil;
    @MockBean private FcmOutboxDispatcher dispatcher;
    @MockBean private FcmTransport transport;

    @Test
    @DisplayName("가족에게 응원 메시지를 보내면 받는 사람의 메시지 탭 목록과 안 읽음 수에 나온다")
    void 가족에게_응원_메시지를_보내면_받는_사람의_메시지_탭에_나온다() {
        // given
        Member senior = members.save(Member.createMember(MemberType.SENIOR, "어르신", "01011112222"));
        Member guardian = members.save(Member.createMember(MemberType.GUARDIAN, "보호자", "01033334444"));
        Family family = families.save(Family.createFamily("CHR001"));
        seniorProfiles.save(SeniorProfile.createSeniorProfile(senior, family, "서울", "CHRINV1",
                LocalDate.of(1950, 1, 1)));
        memberships.save(FamilyMembership.createMembership(family, guardian));
        given(memberUtil.getCurrentMember()).willReturn(guardian);

        // when
        fcmService.sendNotificationToMember(new SendNotificationRequest(senior.getId(), "힘내세요"));
        given(memberUtil.getCurrentMember()).willReturn(senior);
        NotificationCenterResponse messageTab = notificationCenterService.list("MESSAGE", null);

        // then
        assertThat(notifications.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getFcmCategory()).isEqualTo(FcmCategory.HEART_MESSAGE));
        assertThat(messageTab.items()).singleElement()
                .satisfies(item -> assertThat(item.title()).isEqualTo("보호자님이 어르신님에게 응원메시지를 보냈어요."));
        assertThat(messageTab.unreadCounts())
                .containsEntry("MESSAGE", 1)
                .containsEntry("ETC", 0);
    }
}
