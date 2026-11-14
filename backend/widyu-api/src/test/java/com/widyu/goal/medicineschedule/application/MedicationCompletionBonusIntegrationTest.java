package com.widyu.goal.medicineschedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.auth.repository.TemporaryMemberRepository;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.fcm.repository.FcmNotificationRepository;
import com.widyu.fcm.repository.FcmOutboxRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.dto.response.MedicationProofResponse;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.PointHistory;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 완주 보너스 하루 1회(LLD-0038 §11, #940)를 실제 저장소와 포인트 서비스로 확인한다.
 * 단위 테스트는 보너스 조회 결과를 mock으로 정해 두므로, 첫 인증이 남긴 30P 이력을 두 번째 판정이
 * 실제로 읽는지는 여기서만 보인다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "firebase.config-path=/dev/null",
        "s3.credentials.access-key=test",
        "s3.credentials.secret-key=test",
        "s3.region.statics=ap-northeast-2",
        "s3.bucket-name=test-bucket",
        "coolsms.api-key=test",
        "coolsms.api-secret=test",
        "coolsms.api-url=https://api.coolsms.co.kr",
        "coolsms.from-phone-number=01000000000",
        "coolsms.verification-code-length=6",
        "coolsms.verification-code-ttl=300",
        "coolsms.message-template=인증번호: {code}"
})
class MedicationCompletionBonusIntegrationTest {

    private static final String OPERATION_KEY_PREFIX = "MEDICATION_PROOF:";

    @Autowired private MedicationProofTransactionService transactionService;
    @Autowired private MedicationProofRepository proofs;
    @Autowired private MedicineScheduleRepository schedules;
    @Autowired private MemberRepository members;
    @Autowired private FamilyRepository families;
    @Autowired private SeniorProfileRepository profiles;
    @Autowired private PointHistoryRepository histories;
    @Autowired private FcmNotificationRepository notifications;
    @Autowired private FcmOutboxRepository outbox;

    @MockBean private FcmTransport fcmTransport;
    @MockBean private MemberUtil memberUtil;
    @MockBean private TemporaryMemberRepository temporaryMemberRepository;
    @MockBean private RefreshTokenRepository refreshTokenRepository;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @AfterEach
    void 정리한다() {
        outbox.deleteAll();
        notifications.deleteAll();
        histories.deleteAll();
        proofs.deleteAll();
        schedules.deleteAll();
        profiles.deleteAll();
        families.deleteAll();
        members.deleteAll();
    }

    @Test
    @DisplayName("첫 인증으로 완주 보너스를 받은 날 일정을 추가해 다시 모두 채우면 두 번째 인증은 10포인트만 적립한다")
    void 보너스를_받은_날_일정을_추가해_다시_채우면_10포인트만_적립한다() {
        // given: 알람 ±30분 창 안에서 인증해야 하므로 지금 시각의 일정을 쓴다. 두 인증 사이에 날짜가 바뀌면 판정이 달라진다.
        LocalTime now = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        assumeTrue(now.isAfter(LocalTime.of(0, 1)) && now.isBefore(LocalTime.of(23, 58)));
        Member senior = seniorWithProfile();
        long startingPoints = points(senior);
        MedicineSchedule first = schedules.save(MedicineSchedule.create(senior, now));
        LocalDate today = LocalDate.now();

        // when: 오늘 일정 하나를 인증한다.
        MedicationProofResponse firstResponse =
                transactionService.verifyMedication(senior.getId(), first.getId(), List.of());

        // then: 인증 10P + 완주 보너스 20P가 이력 한 건으로 남고, 그날은 보너스를 받은 날이 된다.
        assertThat(firstResponse.earnedPoints()).isEqualTo(30L);
        assertThat(histories.findAll()).singleElement()
                .extracting(PointHistory::getAmount).isEqualTo(30L);
        assertThat(proofs.existsCompletionBonusByMemberAndVerifiedDate(
                senior, today, OPERATION_KEY_PREFIX, MedicationPointPolicy.POINTS_PER_MEDICATION)).isTrue();

        // when: 같은 날 일정을 하나 추가해 그 일정도 인증하면 오늘 일정을 다시 모두 채운다.
        MedicineSchedule added = schedules.save(MedicineSchedule.create(senior, now.minusMinutes(1)));
        MedicationProofResponse secondResponse =
                transactionService.verifyMedication(senior.getId(), added.getId(), List.of());

        // then: 보너스는 날짜별 한 번이라 10P만 더해진다. 하루 합계는 40P다.
        assertThat(secondResponse.earnedPoints()).isEqualTo(10L);
        assertThat(histories.findAll()).extracting(PointHistory::getAmount)
                .containsExactlyInAnyOrder(30L, 10L);
        assertThat(points(senior)).isEqualTo(startingPoints + 40L);
        assertThat(proofs.countByMemberAndVerifiedDate(senior, today)).isEqualTo(2L);
        assertThat(proofs.existsCompletionBonusByMemberAndVerifiedDate(
                senior, today, OPERATION_KEY_PREFIX, MedicationPointPolicy.POINTS_PER_MEDICATION)).isTrue();
    }

    private Member seniorWithProfile() {
        Family family = families.save(Family.createFamily("F94001"));
        Member senior = members.save(Member.createMember(MemberType.SENIOR, "시니어", "01094000001"));
        profiles.save(SeniorProfile.createSeniorProfile(senior, family,
                "서울시 강남구", "I940001", LocalDate.of(1950, 1, 1)));
        return senior;
    }

    private long points(Member senior) {
        return profiles.findByMemberId(senior.getId()).orElseThrow().getPoints();
    }
}
