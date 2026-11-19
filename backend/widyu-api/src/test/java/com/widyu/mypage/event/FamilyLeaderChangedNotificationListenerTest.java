package com.widyu.mypage.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.widyu.fcm.NotificationType;
import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FamilyLeaderChangedNotificationListenerTest {

    @Mock private FcmOutboxService outboxService;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @InjectMocks private FamilyLeaderChangedNotificationListener listener;

    @Test
    @DisplayName("방장이 바뀌면 새 방장에게 R01, 넘긴 본인을 뺀 다른 보호자와 어르신에게 R02를 등록한다")
    void 방장이_바뀌면_새_방장에게_R01_나머지_가족에게_R02를_등록한다() {
        // given: 가족 10, 이전 방장 1, 새 방장 2(김새방), 다른 보호자 3, 어르신 7과 8
        List<FamilyMembership> memberships = List.of(
                membership(1L, null), membership(2L, "김새방"), membership(3L, null));
        given(familyMembershipRepository.findAllByFamilyIdForUpdate(10L)).willReturn(memberships);
        List<SeniorProfile> seniors = List.of(senior(7L), senior(8L));
        given(seniorProfileRepository.findAllByFamilyIdForShare(10L)).willReturn(seniors);
        ArgumentCaptor<Long> recipients = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<FcmSendDto> messages = ArgumentCaptor.forClass(FcmSendDto.class);

        // when
        listener.onLeaderChanged(FamilyLeaderChangedEvent.of(10L, 1L, 2L));

        // then
        verify(outboxService, times(4)).enqueue(recipients.capture(), messages.capture());
        Map<Long, FcmSendDto> sent = new HashMap<>();
        for (int i = 0; i < recipients.getAllValues().size(); i++) {
            sent.put(recipients.getAllValues().get(i), messages.getAllValues().get(i));
        }
        assertThat(sent).containsOnlyKeys(2L, 3L, 7L, 8L);

        FcmSendDto r01 = sent.get(2L);
        assertThat(r01.notificationType()).isEqualTo(NotificationType.FAMILY_LEADER_CHANGED);
        assertThat(r01.title()).isEqualTo("이제 가족 방장이 되었어요.");
        assertThat(r01.content()).isEqualTo("가족 관리와 중요한 알림을 확인해주세요.");
        assertThat(r01.dataForEnqueue("event-1"))
                .containsEntry("notificationType", "FAMILY_LEADER_CHANGED")
                .containsEntry("deepLink", "/family-manage");

        FcmSendDto guardian = sent.get(3L);
        assertThat(guardian.title()).isEqualTo("김새방 님이 가족 방장이 되었어요.");
        assertThat(guardian.content()).isNull();
        assertThat(guardian.dataForEnqueue("event-2")).containsEntry("deepLink", "/family-manage");

        FcmSendDto senior = sent.get(7L);
        assertThat(senior.title()).isEqualTo("김새방 님이 가족 방장이 되었어요.");
        assertThat(senior.content()).isNull();
        assertThat(senior.dataForEnqueue("event-3")).containsEntry("deepLink", "/my/management");
    }

    private static SeniorProfile senior(Long memberId) {
        Member member = mock(Member.class);
        given(member.getId()).willReturn(memberId);
        SeniorProfile profile = mock(SeniorProfile.class);
        given(profile.getMember()).willReturn(member);
        return profile;
    }

    private static FamilyMembership membership(Long guardianId, String name) {
        Member guardian = mock(Member.class);
        given(guardian.getId()).willReturn(guardianId);
        if (name != null) {
            given(guardian.getName()).willReturn(name);
        }
        FamilyMembership membership = mock(FamilyMembership.class);
        given(membership.getGuardian()).willReturn(guardian);
        return membership;
    }
}
