package com.widyu.fcm.event.medicineschedule.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;

import com.widyu.fcm.application.FcmService;
import com.widyu.global.entity.Status;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.repository.FamilyMembershipRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineScheduleNotificationListener 단위 테스트")
class MedicineScheduleNotificationListenerTest {

    @Mock private FcmService fcmService;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;

    @InjectMocks private MedicineScheduleNotificationListener listener;

    @Test
    @DisplayName("복약 알림은 오늘 유효한 스케줄만 조회한다")
    void 복약_알림은_오늘_유효한_스케줄만_조회한다() {
        // given
        given(medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(any(), eq(Status.ACTIVE), any()))
                .willReturn(List.of());

        // when
        listener.checkMedicineSchedules();

        // then: 오늘 날짜 기준 유효한 스케줄만 조회한다
        then(medicineScheduleRepository).should(atLeastOnce())
                .findByAlarmTimeAndStatusEffectiveOn(any(), eq(Status.ACTIVE), eq(LocalDate.now()));
    }
}
