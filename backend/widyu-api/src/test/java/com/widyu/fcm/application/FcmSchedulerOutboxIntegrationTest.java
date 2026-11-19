package com.widyu.fcm.application;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.application.degraded.DegradedAlertMarker;
import com.widyu.fcm.MemberFcmToken;
import com.widyu.fcm.MemberNotificationSetting;
import com.widyu.fcm.NotificationType;
import com.widyu.fcm.PushSettingGroup;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.event.goal.healthschedule.listener.HealthScheduleNotificationListener;
import com.widyu.fcm.event.goal.walk.listener.WalkNotificationListener;
import com.widyu.fcm.repository.*;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.util.MemberUtil;
import com.widyu.global.util.SecurityUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.walk.Walk;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@DataJpaTest(properties = {"fcm.delivery.max-retries=2", "fcm.delivery.normal-ttl=1h", "fcm.delivery.emergency-ttl=1m"})
@ActiveProfiles("test")
@EnableConfigurationProperties({FcmDeliveryProperties.class, FirebaseProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, FcmService.class, FcmOutboxService.class, FcmOutboxTransactions.class,
        FcmEligibility.class, NotificationSettingService.class,         WalkNotificationListener.class, HealthScheduleNotificationListener.class})
class FcmSchedulerOutboxIntegrationTest {
    /** 14:20 회차의 조회 창은 [15:20, 15:30)이다. 실행 시각의 밀리초는 버린다(LLD-0105). */
    private static final LocalDateTime RUN_AT = LocalDateTime.of(2026, 10, 22, 14, 20, 0, 37_000_000);
    private static final LocalDateTime SCHEDULED_AT = LocalDateTime.of(2026, 10, 22, 15, 25);
    @Autowired WalkNotificationListener walkScheduler;
    @Autowired HealthScheduleNotificationListener healthScheduler;
    @Autowired WalkRepository walks;
    @Autowired HealthScheduleRepository schedules;
    @Autowired MemberRepository members;
    @Autowired MemberFcmTokenRepository tokens;
    @Autowired FcmOutboxRepository outbox;
    @Autowired FcmNotificationRepository notifications;
    @Autowired MemberNotificationSettingRepository settings;
    @Autowired FamilyRepository families;
    @Autowired FamilyMembershipRepository memberships;
    @Autowired SeniorProfileRepository seniorProfiles;
    @Autowired FcmOutboxTransactions transactions;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean FcmService fcm;
    @MockBean DegradedAlertMarker degradedAlertMarker;
    @MockBean FcmOutboxDispatcher immediate;
    @MockBean FcmTransport transport;
    @MockBean MemberUtil memberUtil;
    @MockBean SecurityUtil securityUtil;
    @MockBean FamilyAccessService familyAccessService;

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifications.deleteAll();
            outbox.deleteAll();
            walks.deleteAll();
            schedules.deleteAllInBatch();
            settings.deleteAll();
            tokens.deleteAll();
            seniorProfiles.deleteAll();
            memberships.deleteAll();
            families.deleteAll();
            members.deleteAll();
        });
    }

    @Test
    @DisplayName("걷기 스케줄러가 실행되면 쓰기 트랜잭션으로 outbox를 커밋하고 송신한다")
    void 걷기_스케줄러가_outbox를_커밋한다() {
        // given
        createWalk();
        observeEnqueue(false);
        // when: 테스트 트랜잭션 없이 실제 스케줄러 프록시가 쓰기 트랜잭션을 열어야 한다.
        walkScheduler.sendWalkGoalReminderToUnachieved();
        // then
        assertCommittedAndDispatch(FcmCategory.WALK);
    }

    @Test
    @DisplayName("건강 일정 스케줄러가 실행되면 쓰기 트랜잭션으로 outbox를 커밋하고 송신한다")
    void 건강일정_스케줄러가_outbox를_커밋한다() {
        // given
        createSchedule();
        observeEnqueue(false);
        // when
        healthScheduler.sendHealthScheduleReminderAt(RUN_AT);
        // then
        assertCommittedAndDispatch(FcmCategory.HEALTH_SCHEDULE);
    }

    @Test
    @DisplayName("보호자 소유 건강일정이 임박하면 H01 센터 행과 outbox를 만들지 않는다")
    void 보호자_소유_건강일정이_임박하면_H01_센터와_outbox를_만들지_않는다() {
        // given
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                schedules.save(HealthSchedule.create(guardianWithToken(), "병원 방문", "주소", 37.0, 127.0,
                        SCHEDULED_AT)));

        // when
        healthScheduler.sendHealthScheduleReminderAt(RUN_AT);

        // then
        assertThat(notifications.count()).isZero();
        assertThat(outbox.count()).isZero();
        then(fcm).should(org.mockito.Mockito.never()).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    @Test
    @DisplayName("어르신 일정 회차를 두 번 실행하면 일반 알림을 켠 보호자에게 H01-C 센터 행이 하나 남고 끈 보호자에게는 아무것도 남지 않는다")
    void 어르신_일정이_임박하면_가족_보호자마다_H01C_센터_행이_하나씩_남는다() {
        // given
        List<Long> ids = new TransactionTemplate(transactionManager).execute(status -> {
            Member senior = memberWithToken();
            Family family = families.save(Family.createFamily("H01C01"));
            seniorProfiles.save(SeniorProfile.createSeniorProfile(senior, family, "서울", "H01CINV",
                    LocalDate.of(1950, 1, 1)));
            Member enabled = members.save(Member.createMember(MemberType.GUARDIAN, "보호자1", "01087654321"));
            Member disabled = members.save(Member.createMember(MemberType.GUARDIAN, "보호자2", "01087654322"));
            memberships.save(FamilyMembership.createMembership(family, enabled));
            memberships.save(FamilyMembership.createMembership(family, disabled));
            tokens.save(MemberFcmToken.builder().member(enabled).token("guardian-one").active(true).build());
            tokens.save(MemberFcmToken.builder().member(disabled).token("guardian-two").active(true).build());
            settings.save(MemberNotificationSetting.create(disabled, PushSettingGroup.GENERAL, false));
            schedules.save(HealthSchedule.create(senior, "서울대학교병원 정형외과 정기검진", "주소", 37.0, 127.0,
                    SCHEDULED_AT));
            return List.of(senior.getId(), enabled.getId(), disabled.getId());
        });
        Long seniorId = ids.get(0);

        // when: 재시작 등으로 같은 회차가 다시 실행돼도 보호자 센터 행은 늘지 않아야 한다.
        healthScheduler.sendHealthScheduleReminderAt(RUN_AT);
        healthScheduler.sendHealthScheduleReminderAt(RUN_AT);

        // then
        for (Long guardianId : ids.subList(1, 2)) {
            assertThat(notifications.findAll().stream()
                    .filter(row -> row.getRecipientMember().getId().equals(guardianId)).toList())
                    .singleElement().satisfies(row -> {
                        assertThat(row.getType()).isEqualTo(NotificationType.HEALTH_SCHEDULE_UPCOMING);
                        assertThat(row.getSeniorId()).isEqualTo(seniorId);
                        assertThat(row.getTitle()).isEqualTo("오후 3시 25분에 수신자 님의 일정이 있어요.");
                        assertThat(row.getBody()).isEqualTo("서울대학교병원 정형외과 정기… 일정을 확인해보세요.");
                        assertThat(row.getDeepLink()).isEqualTo("/goal");
                    });
        }
        FcmOutbox enabledRow = guardianOutbox(ids.get(1));
        assertThat(enabledRow.getTitle()).isEqualTo("수신자 님의 건강 일정이 곧 있어요.");
        assertThat(enabledRow.getBody()).isEqualTo("앱에서 일정 시간과 내용을 확인해주세요.");
        assertThat(transactions.claim(enabledRow.getId())).isNotNull();
        assertThat(notifications.findAll()).noneMatch(row -> row.getRecipientMember().getId().equals(ids.get(2)));
        assertThat(outbox.findAll()).noneMatch(row -> row.getRecipientMember().getId().equals(ids.get(2)));
    }

    @Test
    @DisplayName("걷기 스케줄러의 커밋이 실패하면 outbox와 송신을 남기지 않는다")
    void 걷기_스케줄러_롤백은_송신하지_않는다() {
        // given
        createWalk();
        observeEnqueue(true);
        // when / then
        assertThatThrownBy(() -> walkScheduler.sendWalkGoalReminderToUnachieved())
                .isInstanceOf(IllegalStateException.class).hasMessage("scheduler commit failure");
        assertRolledBackWithoutDispatch();
    }

    @Test
    @DisplayName("건강 일정 한 건의 커밋이 실패하면 그 일정의 outbox와 송신을 남기지 않고 스케줄러는 계속한다")
    void 건강일정_한_건의_커밋이_실패하면_그_일정은_송신하지_않는다() {
        // given
        createSchedule();
        observeEnqueue(true);
        // when / then: 일정마다 따로 커밋하므로 실패는 그 일정 안에서 끝난다.
        assertThatCode(() -> healthScheduler.sendHealthScheduleReminderAt(RUN_AT)).doesNotThrowAnyException();
        assertRolledBackWithoutDispatch();
    }

    @Test
    @DisplayName("건강 일정 한 건의 커밋이 실패해도 같은 회차의 다른 일정 outbox는 커밋한다")
    void 건강일정_한_건의_커밋이_실패해도_다른_일정은_커밋한다() {
        // given
        Long failing = createScheduleFor("01011110001", "first-loopback");
        Long committed = createScheduleFor("01011110002", "second-loopback");
        willAnswer(invocation -> {
            invocation.callRealMethod();
            if (failing.equals(invocation.getArgument(0))) {
                failNextCommit();
            }
            return null;
        }).given(fcm).sendMessageToUser(anyLong(), any(FcmSendDto.class));

        // when
        healthScheduler.sendHealthScheduleReminderAt(RUN_AT);

        // then
        assertThat(outbox.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getRecipientMember().getId()).isEqualTo(committed));
        assertThat(notifications.count()).isEqualTo(1);
        then(immediate).should().submit(outbox.findAll().getFirst().getId());
        then(immediate).shouldHaveNoMoreInteractions();
    }

    private FcmOutbox guardianOutbox(Long guardianId) {
        return outbox.findAll().stream()
                .filter(row -> row.getRecipientMember().getId().equals(guardianId))
                .findFirst().orElseThrow();
    }

    private void observeEnqueue(boolean failCommit) {
        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            invocation.callRealMethod();
            assertThat(outbox.count()).isEqualTo(1);
            then(immediate).shouldHaveNoInteractions();
            then(transport).shouldHaveNoInteractions();
            if (failCommit) {
                failNextCommit();
            }
            return null;
        }).given(fcm).sendMessageToUser(anyLong(), any(FcmSendDto.class));
    }

    private void failNextCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                throw new IllegalStateException("scheduler commit failure");
            }
        });
    }

    private void assertCommittedAndDispatch(FcmCategory category) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(outbox.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getState()).isEqualTo(FcmOutbox.State.PENDING);
            assertThat(row.getFcmCategory()).isEqualTo(category);
        });
        Long id = outbox.findAll().getFirst().getId();
        then(immediate).should().submit(id);
        given(transport.send(any(FcmDelivery.class), any())).willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(outbox.findById(id).orElseThrow().getState()).isEqualTo(FcmOutbox.State.CLAIMED);
            BooleanSupplier preflight = invocation.getArgument(1);
            assertThat(preflight.getAsBoolean()).isTrue();
            return FcmTransport.Result.delivered();
        });
        FcmOutboxDispatcher dispatcher = dispatcher();
        try {
            dispatcher.dispatch(id);
        } finally {
            dispatcher.shutdown();
        }
        assertThat(outbox.findById(id).orElseThrow().getState()).isEqualTo(FcmOutbox.State.SENT);
        assertThat(notifications.count()).isEqualTo(1);
    }

    private void assertRolledBackWithoutDispatch() {
        assertThat(outbox.count()).isZero();
        assertThat(notifications.count()).isZero();
        then(immediate).shouldHaveNoInteractions();
        FcmOutboxDispatcher dispatcher = dispatcher();
        try {
            dispatcher.poll();
        } finally {
            dispatcher.shutdown();
        }
        then(transport).shouldHaveNoInteractions();
    }

    private FcmOutboxDispatcher dispatcher() {
        return new FcmOutboxDispatcher(transactions, outbox, transport, new FcmSendMetrics(new SimpleMeterRegistry()));
    }

    private void createWalk() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Walk walk = Walk.createWithGoal(memberWithToken(), LocalDate.now(), 10000);
            walk.updateActualSteps(1);
            walks.save(walk);
        });
    }

    private void createSchedule() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                schedules.save(HealthSchedule.create(memberWithToken(), "병원 방문", "주소", 37.0, 127.0,
                        SCHEDULED_AT)));
    }

    private Long createScheduleFor(String phone, String token) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member member = members.save(Member.createMember(MemberType.SENIOR, "수신자", phone));
            tokens.save(MemberFcmToken.builder().member(member).token(token).active(true).build());
            schedules.save(HealthSchedule.create(member, "병원 방문", "주소", 37.0, 127.0, SCHEDULED_AT));
            return member.getId();
        });
    }

    private Member memberWithToken() {
        Member member = members.save(Member.createMember(MemberType.SENIOR, "수신자", "01012345678"));
        tokens.save(MemberFcmToken.builder().member(member).token("loopback-only").active(true).build());
        return member;
    }

    private Member guardianWithToken() {
        Member member = members.save(Member.createMember(MemberType.GUARDIAN, "보호자", "01012345678"));
        tokens.save(MemberFcmToken.builder().member(member).token("guardian-loopback").active(true).build());
        return member;
    }
}
