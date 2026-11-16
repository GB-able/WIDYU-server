package com.widyu.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.global.entity.Status;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.LocalAccount;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.mypage.dto.response.FamilyMemberListResponse;
import com.widyu.pay.Payment;
import com.widyu.pay.PaymentCancel;
import com.widyu.pay.PaymentCard;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.dto.response.PaymentConfirmResponses;
import com.widyu.pay.repository.PaymentRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 목록 조회의 SQL 실행 수가 행 수에 비례해 늘지 않는지 확인한다(#1057, LLD-0161).
 * 회원의 local_account, senior_profile과 결제 수단 4종, 취소 이력을 목록 쿼리가 함께 읽는지 본다.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ListQueryCountTest {

    private static final int SMALL = 5;
    private static final int LARGE = 20;
    private static final LocalTime ALARM_TIME = LocalTime.of(9, 0);

    @Autowired private TestEntityManager entityManager;
    @Autowired private FamilyMembershipRepository membershipRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MedicineScheduleRepository medicineScheduleRepository;

    @Test
    @DisplayName("가족 구성원 목록을 조회하면 보호자 수가 늘어도 SQL 실행 수가 같다")
    void 가족_구성원_목록을_조회하면_보호자_수가_늘어도_SQL_실행_수가_같다() {
        // given
        Long smallFamilyId = saveFamily("SML001", SMALL);
        Long largeFamilyId = saveFamily("LRG001", LARGE);

        // when
        long small = countStatements(() -> readFamilyMembers(smallFamilyId));
        long large = countStatements(() -> readFamilyMembers(largeFamilyId));

        // then
        report("family", small, large);
        assertThat(large).isEqualTo(small);
    }

    @Test
    @DisplayName("가족 알림 대상 보호자를 조회하면 보호자 수가 늘어도 SQL 실행 수가 같다")
    void 가족_알림_대상_보호자를_조회하면_보호자_수가_늘어도_SQL_실행_수가_같다() {
        // given
        Long smallFamilyId = saveFamily("SML002", SMALL);
        Long largeFamilyId = saveFamily("LRG002", LARGE);

        // when
        long small = countStatements(() -> readGuardianIds(smallFamilyId));
        long large = countStatements(() -> readGuardianIds(largeFamilyId));

        // then
        report("family-guardians", small, large);
        assertThat(large).isEqualTo(small);
    }

    @Test
    @DisplayName("회원 결제 목록을 조회하면 결제 수가 늘어도 SQL 실행 수가 같다")
    void 회원_결제_목록을_조회하면_결제_수가_늘어도_SQL_실행_수가_같다() {
        // given
        Long smallMemberId = savePayments("small", SMALL);
        Long largeMemberId = savePayments("large", LARGE);

        // when
        long small = countStatements(() -> PaymentConfirmResponses.from(paymentRepository.findByMemberId(smallMemberId)));
        long large = countStatements(() -> PaymentConfirmResponses.from(paymentRepository.findByMemberId(largeMemberId)));
        entityManager.clear();
        List<Payment> payments = paymentRepository.findByMemberId(largeMemberId);

        // then
        report("payment", small, large);
        assertThat(large).isEqualTo(small);
        assertThat(payments).hasSize(LARGE);
        assertThat(payments.get(0).getCancellations()).hasSize(2);
    }

    @Test
    @DisplayName("복약 알림 대상을 조회하면 시니어 수가 늘어도 SQL 실행 수가 같다")
    void 복약_알림_대상을_조회하면_시니어_수가_늘어도_SQL_실행_수가_같다() {
        // given
        LocalDate today = LocalDate.now();
        saveMedicineSchedules("small", ALARM_TIME, SMALL);
        saveMedicineSchedules("large", ALARM_TIME.plusMinutes(1), LARGE);

        // when
        long small = countStatements(() -> readMedicineTargets(ALARM_TIME, today));
        long large = countStatements(() -> readMedicineTargets(ALARM_TIME.plusMinutes(1), today));

        // then
        report("medicine", small, large);
        assertThat(large).isEqualTo(small);
    }

    private Long saveFamily(String familyCode, int guardianCount) {
        Family family = entityManager.persist(Family.createFamily(familyCode));
        Member senior = entityManager.persist(Member.createMember(MemberType.SENIOR, "시니어", null));
        entityManager.persist(SeniorProfile.createSeniorProfile(senior, family, "주소", familyCode + "X", LocalDate.of(1950, 1, 1)));
        for (int i = 0; i < guardianCount; i++) {
            Member guardian = entityManager.persist(Member.createMember(MemberType.GUARDIAN, "보호자" + i, null));
            entityManager.persist(LocalAccount.createLocalAccount(guardian, familyCode + i + "@test.com", "pw"));
            entityManager.persist(FamilyMembership.createMembership(family, guardian, i));
        }
        entityManager.flush();
        entityManager.clear();
        return family.getId();
    }

    private FamilyMemberListResponse readFamilyMembers(Long familyId) {
        List<FamilyMembership> memberships = membershipRepository.findAllByFamilyIdWithGuardianOrdered(familyId);
        List<SeniorProfile> seniors = seniorProfileRepository.findAllByFamilyIdWithMember(familyId);
        return FamilyMemberListResponse.of(memberships, seniors, memberships.get(0).getGuardian().getId(), 0);
    }

    private List<Long> readGuardianIds(Long familyId) {
        return membershipRepository.findAllByFamilyIdWithGuardian(familyId).stream()
                .map(membership -> membership.getGuardian().getId())
                .toList();
    }

    private Long savePayments(String prefix, int paymentCount) {
        Member member = entityManager.persist(Member.createMember(MemberType.GUARDIAN, "결제자", null));
        for (int i = 0; i < paymentCount; i++) {
            Payment payment = Payment.builder()
                    .paymentKey(prefix + "-key-" + i)
                    .orderId(prefix + "-order-" + i)
                    .amount(1000)
                    .status(PaymentStatus.DONE)
                    .member(member)
                    .build();
            payment.assignCard(PaymentCard.builder().number("1234-****").build());
            if (i == 0) {
                // 취소 이력 컬렉션을 함께 읽어도 결제 행이 중복되지 않는지 보려고 첫 결제에만 두 건을 둔다.
                payment.addCancellation(PaymentCancel.createPending(payment, 100, 100, 0, "부분 취소", member.getId(),
                        prefix + "-cancel-a", prefix + "-pg-a"));
                payment.addCancellation(PaymentCancel.createPending(payment, 100, 100, 0, "부분 취소", member.getId(),
                        prefix + "-cancel-b", prefix + "-pg-b"));
            }
            entityManager.persist(payment);
        }
        entityManager.flush();
        entityManager.clear();
        return member.getId();
    }

    private void saveMedicineSchedules(String prefix, LocalTime alarmTime, int seniorCount) {
        for (int i = 0; i < seniorCount; i++) {
            Member senior = entityManager.persist(Member.createMember(MemberType.SENIOR, prefix + i, null));
            entityManager.persist(SeniorProfile.createSeniorProfile(senior, null, "주소",
                    String.format("%.1sMED%02d", prefix, i), LocalDate.of(1950, 1, 1)));
            entityManager.persist(MedicineSchedule.create(senior, alarmTime, LocalDate.now().minusDays(1)));
        }
        entityManager.flush();
        entityManager.clear();
    }

    // 복약 알림 리스너가 읽는 값(알림 리비전, 시니어 프로필의 가족)을 같은 순서로 읽는다.
    private List<Long> readMedicineTargets(LocalTime alarmTime, LocalDate date) {
        return medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(alarmTime, Status.ACTIVE, date)
                .stream()
                .filter(schedule -> schedule.getMember().getSeniorProfile() != null)
                .map(schedule -> schedule.getMember().getMedicationAlarmRevision())
                .toList();
    }

    private long countStatements(Supplier<?> query) {
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        query.get();
        return statistics.getPrepareStatementCount();
    }

    private void report(String path, long small, long large) {
        System.out.printf("[list-query-count] path=%s rows=%d statements=%d rows=%d statements=%d%n",
                path, SMALL, small, LARGE, large);
    }
}
