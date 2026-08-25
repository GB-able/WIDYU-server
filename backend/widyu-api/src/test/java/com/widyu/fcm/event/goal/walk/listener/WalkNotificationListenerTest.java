package com.widyu.fcm.event.goal.walk.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.fcm.application.FcmService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.walk.Walk;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalkNotificationListener 단위 테스트")
class WalkNotificationListenerTest {

    @Mock private FcmService fcmService;
    @Mock private WalkRepository walkRepository;

    @InjectMocks private WalkNotificationListener listener;

    @Test
    @DisplayName("걷기 목표 미달성 알림을 보내면 걸음 수가 제목에 포함된다")
    void 걷기_목표_미달성_알림을_보내면_걸음_수가_제목에_포함된다() {
        // given
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Walk walk = Walk.createWithGoal(member, LocalDate.now(), 10000);
        walk.updateActualSteps(6500);
        given(walkRepository.findUnachievedWalksByDate(any())).willReturn(List.of(walk));
        ArgumentCaptor<FcmSendDto> notificationCaptor = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.sendWalkGoalReminderToUnachieved();

        // then
        then(fcmService).should().sendMessageToUser(any(), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().title())
                .isEqualTo("목표 10000보 중 6500보를 걸으셨어요. 조금만 더 힘내세요!");
        assertThat(notificationCaptor.getValue().content()).isEqualTo("오늘의 걷기 목표를 확인해주세요.");
    }
}
