package com.widyu.fcm.event.goal.healthschedule.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthScheduleNotificationListener 단위 테스트")
class HealthScheduleNotificationListenerTest {

    @Mock private FcmService fcmService;
    @Mock private HealthScheduleRepository healthScheduleRepository;

    @InjectMocks private HealthScheduleNotificationListener listener;

    @Test
    @DisplayName("건강 일정 알림을 보내면 시간과 일정명이 제목에 포함된다")
    void 건강_일정_알림을_보내면_시간과_일정명이_제목에_포함된다() {
        // given
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        HealthSchedule schedule = HealthSchedule.create(
                member,
                "병원 진료",
                null,
                null,
                null,
                LocalDateTime.of(2026, 8, 25, 15, 30)
        );
        given(healthScheduleRepository.findUpcomingSchedulesInTimeRange(any(), any()))
                .willReturn(List.of(schedule));
        ArgumentCaptor<FcmSendDto> notificationCaptor = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.sendHealthScheduleReminder();

        // then
        then(fcmService).should().sendMessageToUser(any(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().title()).isEqualTo("15:30에 '병원 진료' 일정이 있어요!");
        assertThat(notificationCaptor.getValue().content()).isEqualTo("건강 일정을 잊지 말고 확인해주세요.");
    }
}
