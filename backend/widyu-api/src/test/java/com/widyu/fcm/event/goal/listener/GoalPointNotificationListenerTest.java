package com.widyu.fcm.event.goal.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.fcm.event.goal.dto.GoalAchievedEvent;
import com.widyu.fcm.event.goal.dto.GoalsAchievedEvent;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoalPointNotificationListenerTest {

    @Mock private FcmOutboxService outboxService;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @InjectMocks private GoalPointNotificationListener listener;

    @Test
    @DisplayName("목표 사건 키를 만들면 지정된 형식을 지키고 40자 이내로 만든다")
    void 목표_사건_키를_만들면_형식과_길이를_지킨다() {
        List<String> eventIds = List.of(
                GoalAchievedEvent.forWalk(1L, Long.MAX_VALUE, 25L).eventId(),
                GoalAchievedEvent.forHealthSchedule(1L, Long.MAX_VALUE, 100L).eventId(),
                GoalAchievedEvent.forMedicationDay(1L, Long.MAX_VALUE, 30L).eventId());

        assertThat(eventIds).containsExactly(
                "G01:W:" + Long.MAX_VALUE, "G01:H:" + Long.MAX_VALUE, "G01:M:" + Long.MAX_VALUE);
        assertThat(eventIds).allMatch(id -> id.length() <= 40);
    }

    @Test
    @DisplayName("걷기 목표를 달성하면 시니어와 같은 가족 보호자에게 G01을 기록한다")
    void 걷기_목표를_달성하면_가족에게_G01을_기록한다() {
        // given
        Member senior = member(1L, MemberType.SENIOR, "부모님");
        Member guardian = member(2L, MemberType.GUARDIAN, "보호자");
        Family family = Family.createFamily("ABC123");
        ReflectionTestUtils.setField(family, "id", 3L);
        SeniorProfile profile = SeniorProfile.createSeniorProfile(
                senior, family, "서울", "INV1234", LocalDate.of(1950, 1, 1));
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(profile));
        given(familyMembershipRepository.findAllByFamilyIdWithGuardian(3L))
                .willReturn(List.of(FamilyMembership.createMembership(family, guardian)));
        ArgumentCaptor<Long> recipient = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<FcmSendDto> message = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.onGoalAchieved(GoalAchievedEvent.forWalk(1L, 7L, 25L));

        // then
        then(outboxService).should(org.mockito.Mockito.times(2)).enqueue(recipient.capture(), message.capture());
        assertThat(recipient.getAllValues()).containsExactly(1L, 2L);
        assertThat(message.getAllValues()).allMatch(item -> item.notificationType() == NotificationType.GOAL_ACHIEVED);
        assertThat(message.getAllValues()).allMatch(item -> item.eventId().equals("G01:W:7"));
        assertThat(message.getAllValues()).allMatch(item -> item.content().equals("25P가 자동으로 적립됐어요."));
        assertThat(message.getAllValues().get(0).title()).isEqualTo("걷기 목표를 달성했어요!");
        assertThat(message.getAllValues().get(0).dataForEnqueue("G01:W:7"))
                .containsEntry("deepLink", "/goal");
        assertThat(message.getAllValues().get(1).title()).isEqualTo("부모님 님이 걷기 목표를 달성했어요!");
        assertThat(message.getAllValues().get(1).relatedMemberId()).isEqualTo(1L);
        assertThat(message.getAllValues().get(1).deepLink())
                .isEqualTo("/goal/walk?seniorId=1");
        assertThat(message.getAllValues().get(1).dataForEnqueue("G01:W:7"))
                .containsEntry("deepLink", "/goal/walk?seniorId=1");
    }

    @Test
    @DisplayName("목표 종류별로 보호자 G01-C 딥링크는 달성한 목표의 위듀케어 화면이다")
    void 목표_종류별로_보호자_딥링크는_달성한_목표_화면이다() {
        // when
        List<String> deepLinks = List.of(
                GoalAchievedEvent.forWalk(1L, 7L, 25L).guardianDeepLink(),
                GoalAchievedEvent.forHealthSchedule(1L, 8L, 100L).guardianDeepLink(),
                GoalAchievedEvent.forMedicationDay(1L, 9L, 30L).guardianDeepLink());

        // then
        assertThat(deepLinks).containsExactly(
                "/goal/walk?seniorId=1", "/goal/hospital?seniorId=1", "/goal/medicine?seniorId=1");
    }

    @Test
    @DisplayName("건강 일정 여러 개를 한 번에 달성하면 시니어와 보호자에게 G02 하나를 기록한다")
    void 건강_일정_여러_개를_한_번에_달성하면_G02_하나를_기록한다() {
        // given
        Member senior = member(1L, MemberType.SENIOR, "부모님");
        Member guardian = member(2L, MemberType.GUARDIAN, "보호자");
        Family family = Family.createFamily("ABC123");
        ReflectionTestUtils.setField(family, "id", 3L);
        SeniorProfile profile = SeniorProfile.createSeniorProfile(
                senior, family, "서울", "INV1234", LocalDate.of(1950, 1, 1));
        given(seniorProfileRepository.findByMemberId(1L)).willReturn(Optional.of(profile));
        given(familyMembershipRepository.findAllByFamilyIdWithGuardian(3L))
                .willReturn(List.of(FamilyMembership.createMembership(family, guardian)));
        ArgumentCaptor<Long> recipient = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<FcmSendDto> message = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.onGoalsAchieved(GoalsAchievedEvent.forHealthSchedules(1L, List.of(12L, 11L), 200L));

        // then
        then(outboxService).should(org.mockito.Mockito.times(2)).enqueue(recipient.capture(), message.capture());
        assertThat(recipient.getAllValues()).containsExactly(1L, 2L);
        assertThat(message.getAllValues())
                .allMatch(item -> item.notificationType() == NotificationType.GOALS_ACHIEVED_GROUPED);
        assertThat(message.getAllValues()).allMatch(item -> item.eventId().equals("G02:H:11:2"));
        assertThat(message.getAllValues()).allMatch(item -> item.content().equals("총 200P가 자동으로 적립됐어요."));
        assertThat(message.getAllValues().get(0).title()).isEqualTo("오늘 목표 2개를 달성했어요!");
        assertThat(message.getAllValues().get(0).dataForEnqueue("G02:H:11:2")).containsEntry("deepLink", "/goal");
        assertThat(message.getAllValues().get(1).title()).isEqualTo("부모님 님이 목표 2개를 달성했어요!");
        assertThat(message.getAllValues().get(1).deepLink()).isEqualTo("/goal?seniorId=1");
    }

    private Member member(Long id, MemberType type, String name) {
        Member member = Member.createMember(type, name, "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
