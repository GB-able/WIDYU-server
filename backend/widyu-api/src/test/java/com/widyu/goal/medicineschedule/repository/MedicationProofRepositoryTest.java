package com.widyu.goal.medicineschedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@DisplayName("MedicationProofRepository 기간 조회 테스트")
class MedicationProofRepositoryTest {

    @Autowired private MedicationProofRepository medicationProofRepository;
    @Autowired private TestEntityManager entityManager;
    @MockBean private JPAQueryFactory jpaQueryFactory;

    private Member persistSenior(String phoneNumber) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", phoneNumber);
        entityManager.persistAndFlush(member);
        return member;
    }

    private MedicineSchedule persistSchedule(Member member, LocalDate effectiveFrom) {
        MedicineSchedule schedule = MedicineSchedule.create(member, LocalTime.of(8, 0));
        ReflectionTestUtils.setField(schedule, "effectiveFrom", effectiveFrom);
        entityManager.persistAndFlush(schedule);
        return schedule;
    }

    private MedicationProof persistProof(MedicineSchedule schedule, Member member, LocalDateTime verifiedAt) {
        MedicationProof proof = MedicationProof.create(schedule, member, List.of());
        ReflectionTestUtils.setField(proof, "verifiedAt", verifiedAt);
        entityManager.persistAndFlush(proof);
        return proof;
    }

    @Test
    @DisplayName("본인의 기간 내 인증만 조회하고 다른 회원·기간 밖 인증은 제외한다")
    void 본인의_기간_내_인증만_조회한다() {
        // given
        Member member = persistSenior("01011112222");
        Member other = persistSenior("01033334444");
        MedicineSchedule schedule = persistSchedule(member, LocalDate.of(2026, 8, 1));
        MedicineSchedule otherSchedule = persistSchedule(other, LocalDate.of(2026, 8, 1));

        MedicationProof inRange = persistProof(schedule, member, LocalDateTime.of(2026, 8, 10, 8, 5));
        persistProof(schedule, member, LocalDateTime.of(2026, 7, 31, 8, 5));   // 기간 밖
        persistProof(otherSchedule, other, LocalDateTime.of(2026, 8, 10, 8, 5)); // 다른 회원

        // when
        List<MedicationProof> proofs = medicationProofRepository.findByMemberIdAndDateRange(
                member.getId(),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 14, 23, 59, 59)
        );

        // then
        assertThat(proofs).extracting(MedicationProof::getId).containsExactly(inRange.getId());
    }
}
