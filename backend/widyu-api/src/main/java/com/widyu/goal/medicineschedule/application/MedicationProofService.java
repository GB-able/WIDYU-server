package com.widyu.goal.medicineschedule.application;

import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationProofService {

    private static final int ALLOWED_TIME_WINDOW_MINUTES = 30;

    private final MedicationProofRepository medicationProofRepository;
    private final MedicineScheduleRepository medicineScheduleRepository;
    private final MemberUtil memberUtil;

    @Transactional
    public void verifyMedication(Long scheduleId, List<MultipartFile> images) {
        Member currentMember = memberUtil.getCurrentMember();

        MedicineSchedule schedule = medicineScheduleRepository
                .findByIdAndStatusWithDetails(scheduleId, Status.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST,
                        "존재하지 않는 약 복용 스케줄입니다."));

        if (!schedule.getMember().getId().equals(currentMember.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "해당 스케줄에 접근할 권한이 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalTime alarmTime = schedule.getAlarmTime();

        // 알람 시간 전후 30분 체크
        LocalDateTime alarmDateTime = now.toLocalDate().atTime(alarmTime);
        LocalDateTime earliestAllowed = alarmDateTime.minusMinutes(ALLOWED_TIME_WINDOW_MINUTES);
        LocalDateTime latestAllowed = alarmDateTime.plusMinutes(ALLOWED_TIME_WINDOW_MINUTES);

        if (now.isBefore(earliestAllowed) || now.isAfter(latestAllowed)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    String.format("약 복용 인증은 알람 시간 전후 %d분 이내에만 가능합니다.",
                            ALLOWED_TIME_WINDOW_MINUTES));
        }

        // 오늘 이미 인증했는지 체크
        boolean alreadyVerifiedToday = medicationProofRepository.existsByMedicineScheduleAndVerifiedAtBetween(
                schedule,
                now.toLocalDate().atStartOfDay(),
                now.toLocalDate().atTime(LocalTime.MAX)
        );

        if (alreadyVerifiedToday) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "오늘은 이미 해당 약 복용을 인증했습니다.");
        }

        // TODO: S3 업로드 로직 추가 필요
        // 현재는 임시로 빈 URL 리스트 사용
        List<String> imageUrls = List.of();

        // 이미지 파일이 있다면 업로드 처리 (추후 S3 서비스 연동)
        if (images != null && !images.isEmpty()) {
            // imageUrls = s3Service.uploadFiles(images);
            log.warn("이미지 업로드 기능은 아직 구현되지 않았습니다. 이미지 개수: {}", images.size());
        }

        MedicationProof proof = MedicationProof.create(schedule, currentMember, imageUrls);
        medicationProofRepository.save(proof);

        log.info("약 복용 인증 완료: scheduleId={}, memberId={}, verifiedAt={}",
                scheduleId, currentMember.getId(), now);
    }
}
