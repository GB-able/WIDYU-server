package com.widyu.goal.healthschedule.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HealthScheduleProgressService {

    private final HealthScheduleRepository healthScheduleRepository;
    private final SeniorProfileRepository seniorProfileRepository;
    private final FamilyConnectionRepository familyConnectionRepository;
    private final MemberUtil memberUtil;

    /**
     * 시니어가 건강 일정을 완료 처리
     */
    @Transactional
    public void completeSchedule(Long healthScheduleId) {
        Member currentMember = memberUtil.getCurrentMember();

        HealthSchedule healthSchedule = healthScheduleRepository.findById(healthScheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "건강 일정을 찾을 수 없습니다."));

        // 권한 체크
        validateHealthScheduleAccess(healthSchedule, currentMember);

        // COMPLETED로 변경
        healthSchedule.complete();
    }

    private void validateHealthScheduleAccess(HealthSchedule healthSchedule, Member currentMember) {
        if (currentMember.getType() == MemberType.SENIOR) {
            // 시니어는 본인의 일정만 접근 가능
            if (!healthSchedule.getMember().getId().equals(currentMember.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "해당 일정에 접근할 권한이 없습니다.");
            }
        } else if (currentMember.getType() == MemberType.GUARDIAN) {
            // 보호자는 연결된 시니어의 일정만 접근 가능
            SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(healthSchedule.getMember().getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SENIOR_PROFILE_NOT_FOUND));

            boolean isConnected = familyConnectionRepository.existsBySeniorIdAndGuardianId(
                    seniorProfile.getId(), currentMember.getId()
            );

            if (!isConnected) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "해당 일정에 접근할 권한이 없습니다.");
            }
        }
    }

    /**
     * 어제 하루치 UPCOMING 일정을 INCOMPLETE로 변경
     */
    @Transactional
    public void markOverdueSchedulesAsIncomplete() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime yesterdayStart = now.minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<HealthSchedule> overdueSchedules = healthScheduleRepository.findByStatusAndDateRange(
                ProgressStatus.UPCOMING, yesterdayStart, todayStart
        );

        for (HealthSchedule schedule : overdueSchedules) {
            schedule.markIncomplete();
        }
    }
}