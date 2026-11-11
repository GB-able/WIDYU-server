package com.widyu.goal.medicineschedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.medicine.MedicineSchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@DisplayName("MedicineScheduleRepository 유효기간 쿼리 테스트")
class MedicineScheduleRepositoryTest {

    @Autowired private MedicineScheduleRepository medicineScheduleRepository;
    @Autowired private TestEntityManager entityManager;
    @MockBean private JPAQueryFactory jpaQueryFactory;

    private Member persistSenior(String phoneNumber) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", phoneNumber);
        entityManager.persistAndFlush(member);
        return member;
    }

    private MedicineSchedule persistSchedule(
            Member member,
            LocalTime alarmTime,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        MedicineSchedule schedule = MedicineSchedule.create(member, alarmTime);
        ReflectionTestUtils.setField(schedule, "effectiveFrom", effectiveFrom);
        ReflectionTestUtils.setField(schedule, "effectiveTo", effectiveTo);
        entityManager.persistAndFlush(schedule);
        return schedule;
    }

    @Test
    @DisplayName("특정 날짜에 유효한 스케줄 버전만 조회한다")
    void 특정_날짜에_유효한_스케줄_버전만_조회한다() {
        // given
        Member member = persistSenior("01011112222");
        MedicineSchedule closed = persistSchedule(
                member, LocalTime.of(8, 0), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));
        MedicineSchedule current = persistSchedule(
                member, LocalTime.of(9, 0), LocalDate.of(2026, 7, 11), null);
        persistSchedule(member, LocalTime.of(10, 0), LocalDate.of(2026, 8, 1), null);

        // when
        List<MedicineSchedule> july10Schedules = medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(
                member, Status.ACTIVE, LocalDate.of(2026, 7, 10));
        List<MedicineSchedule> july11Schedules = medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(
                member, Status.ACTIVE, LocalDate.of(2026, 7, 11));

        // then
        assertThat(july10Schedules).extracting(MedicineSchedule::getId).containsExactly(closed.getId());
        assertThat(july11Schedules).extracting(MedicineSchedule::getId).containsExactly(current.getId());
    }

    @Test
    @DisplayName("알람 시각과 날짜를 조회하면 오늘은 기존 버전이고 내일은 새 버전을 반환한다")
    void 알람_시각과_날짜를_조회하면_오늘은_기존_버전이고_내일은_새_버전을_반환한다() {
        // given
        Member member = persistSenior("01044445555");
        LocalDate today = LocalDate.of(2026, 10, 1);
        MedicineSchedule oldSchedule = persistSchedule(
                member, LocalTime.of(8, 0), today.minusDays(3), today);
        MedicineSchedule newSchedule = persistSchedule(
                member, LocalTime.of(9, 0), today.plusDays(1), null);

        // when
        List<MedicineSchedule> todayOldAlarm = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(8, 0), Status.ACTIVE, today);
        List<MedicineSchedule> todayNewAlarm = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(9, 0), Status.ACTIVE, today);
        List<MedicineSchedule> tomorrowOldAlarm = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(8, 0), Status.ACTIVE, today.plusDays(1));
        List<MedicineSchedule> tomorrowNewAlarm = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(9, 0), Status.ACTIVE, today.plusDays(1));

        // then
        assertThat(todayOldAlarm).extracting(MedicineSchedule::getId).containsExactly(oldSchedule.getId());
        assertThat(todayNewAlarm).isEmpty();
        assertThat(tomorrowOldAlarm).isEmpty();
        assertThat(tomorrowNewAlarm).extracting(MedicineSchedule::getId).containsExactly(newSchedule.getId());
    }

    @Test
    @DisplayName("내일 시작하는 신규 일정을 조회하면 오늘은 없고 내일은 반환한다")
    void 내일_시작하는_신규_일정을_조회하면_오늘은_없고_내일은_반환한다() {
        // given
        Member member = persistSenior("01055556666");
        LocalDate today = LocalDate.of(2026, 10, 1);
        MedicineSchedule schedule = persistSchedule(
                member, LocalTime.of(10, 0), today.plusDays(1), null);

        // when
        List<MedicineSchedule> todaySchedules = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(10, 0), Status.ACTIVE, today);
        List<MedicineSchedule> tomorrowSchedules = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(10, 0), Status.ACTIVE, today.plusDays(1));

        // then
        assertThat(todaySchedules).isEmpty();
        assertThat(tomorrowSchedules).extracting(MedicineSchedule::getId).containsExactly(schedule.getId());
    }

    @Test
    @DisplayName("오늘까지로 마감한 일정을 조회하면 오늘만 반환한다")
    void 오늘까지로_마감한_일정을_조회하면_오늘만_반환한다() {
        // given
        Member member = persistSenior("01066667777");
        LocalDate today = LocalDate.of(2026, 10, 1);
        MedicineSchedule schedule = persistSchedule(
                member, LocalTime.of(11, 0), today.minusDays(3), today);
        persistSchedule(member, LocalTime.of(12, 0), today.plusDays(1), today);

        // when
        List<MedicineSchedule> todaySchedules = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(11, 0), Status.ACTIVE, today);
        List<MedicineSchedule> tomorrowSchedules = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(11, 0), Status.ACTIVE, today.plusDays(1));
        List<MedicineSchedule> cancelledPending = medicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn(
                LocalTime.of(12, 0), Status.ACTIVE, today.plusDays(1));

        // then
        assertThat(todaySchedules).extracting(MedicineSchedule::getId).containsExactly(schedule.getId());
        assertThat(tomorrowSchedules).isEmpty();
        assertThat(cancelledPending).isEmpty();
    }

    @Test
    @DisplayName("알람 동기화용으로 조회하면 오늘까지 유효한 버전과 내일 시작하는 버전을 함께 반환한다")
    void 알람_동기화용으로_조회하면_오늘까지_유효한_버전과_내일_시작하는_버전을_함께_반환한다() {
        // given: 오늘 인증이 끝난 뒤 08시를 지우고, 12시를 20시로 바꾸고, 07시를 새로 추가한 상태
        Member member = persistSenior("01077778888");
        LocalDate today = LocalDate.of(2026, 11, 9);
        persistSchedule(member, LocalTime.of(6, 0), today.minusDays(5), today.minusDays(1));
        MedicineSchedule deletedAfterDone = persistSchedule(
                member, LocalTime.of(8, 0), today.minusDays(5), today);
        MedicineSchedule oldTime = persistSchedule(member, LocalTime.of(12, 0), today.minusDays(5), today);
        MedicineSchedule newTime = persistSchedule(member, LocalTime.of(20, 0), today.plusDays(1), null);
        MedicineSchedule addedForTomorrow = persistSchedule(member, LocalTime.of(7, 0), today.plusDays(1), null);
        MedicineSchedule unchanged = persistSchedule(member, LocalTime.of(19, 27), today.minusDays(5), null);
        persistSchedule(member, LocalTime.of(9, 0), today.plusDays(1), today);
        persistSchedule(persistSenior("01099990000"), LocalTime.of(10, 0), today.minusDays(5), null);

        // when
        List<MedicineSchedule> schedules = medicineScheduleRepository.findEffectiveFromDateByMemberWithDetails(
                member, Status.ACTIVE, today);

        // then: 어제 끝난 06시, 시작 전에 취소된 09시, 다른 회원 일정은 빠진다
        assertThat(schedules).extracting(MedicineSchedule::getId).containsExactly(
                addedForTomorrow.getId(), deletedAfterDone.getId(), oldTime.getId(),
                unchanged.getId(), newTime.getId());
    }

    @Test
    @DisplayName("같은 시각 중복을 확인하면 종료되지 않은 알람만 보고 종료된 옛 알람과 다른 회원은 뺀다")
    void 같은_시각_중복을_확인하면_종료되지_않은_알람만_보고_종료된_옛_알람과_다른_회원은_뺀다() {
        // given: 08시는 오늘까지로 끝났고, 09시는 계속, 20시는 내일부터 시작한다
        Member member = persistSenior("01088889999");
        LocalDate today = LocalDate.of(2026, 11, 9);
        persistSchedule(member, LocalTime.of(8, 0), today.minusDays(5), today);
        persistSchedule(member, LocalTime.of(9, 0), today.minusDays(5), null);
        persistSchedule(member, LocalTime.of(20, 0), today.plusDays(1), null);
        persistSchedule(persistSenior("01088880000"), LocalTime.of(7, 0), today.minusDays(5), null);

        // when / then
        assertThat(medicineScheduleRepository.existsByMemberAndStatusAndAlarmTimeAndEffectiveToIsNull(
                member, Status.ACTIVE, LocalTime.of(8, 0))).isFalse();
        assertThat(medicineScheduleRepository.existsByMemberAndStatusAndAlarmTimeAndEffectiveToIsNull(
                member, Status.ACTIVE, LocalTime.of(9, 0))).isTrue();
        assertThat(medicineScheduleRepository.existsByMemberAndStatusAndAlarmTimeAndEffectiveToIsNull(
                member, Status.ACTIVE, LocalTime.of(20, 0))).isTrue();
        assertThat(medicineScheduleRepository.existsByMemberAndStatusAndAlarmTimeAndEffectiveToIsNull(
                member, Status.ACTIVE, LocalTime.of(7, 0))).isFalse();
    }

    @Test
    @DisplayName("월 범위와 겹치는 스케줄 버전만 조회한다")
    void 월_범위와_겹치는_스케줄_버전만_조회한다() {
        // given
        Member member = persistSenior("01022223333");
        persistSchedule(member, LocalTime.of(7, 0), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        MedicineSchedule overlapping = persistSchedule(
                member, LocalTime.of(8, 0), LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 2));
        MedicineSchedule current = persistSchedule(member, LocalTime.of(9, 0), LocalDate.of(2026, 7, 15), null);
        persistSchedule(member, LocalTime.of(10, 0), LocalDate.of(2026, 8, 1), null);

        // when
        List<MedicineSchedule> schedules = medicineScheduleRepository.findEffectiveByMemberAndDateRange(
                member, Status.ACTIVE, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        // then
        assertThat(schedules).extracting(MedicineSchedule::getId)
                .containsExactlyInAnyOrder(overlapping.getId(), current.getId());
    }

    @Test
    @DisplayName("effectiveFrom이 누락된 기존 스케줄을 createdAt 기준으로 보정한다")
    void effectiveFrom이_누락된_기존_스케줄을_createdAt_기준으로_보정한다() {
        // given
        Member member = persistSenior("01033334444");
        MedicineSchedule schedule = persistSchedule(member, LocalTime.of(8, 0), null, null);
        entityManager.clear();

        // when
        int updatedCount = medicineScheduleRepository.backfillMissingEffectiveFrom();
        entityManager.clear();

        // then
        MedicineSchedule reloaded = entityManager.find(MedicineSchedule.class, schedule.getId());
        assertThat(updatedCount).isEqualTo(1);
        assertThat(reloaded.getEffectiveFrom()).isNotNull();
    }
}
