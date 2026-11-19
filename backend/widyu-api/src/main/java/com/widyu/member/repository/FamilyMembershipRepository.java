package com.widyu.member.repository;

import com.widyu.member.FamilyMembership;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMembershipRepository extends JpaRepository<FamilyMembership, Long> {

    Optional<FamilyMembership> findByGuardianId(Long guardianId);

    @Query("SELECT fm.family.id FROM FamilyMembership fm WHERE fm.guardian.id = :guardianId")
    Optional<Long> findFamilyIdByGuardianId(@Param("guardianId") Long guardianId);

    void deleteByGuardianId(Long guardianId);

    Optional<FamilyMembership> findByFamilyIdAndGuardianId(Long familyId, Long guardianId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fm FROM FamilyMembership fm WHERE fm.family.id = :familyId AND fm.guardian.id = :guardianId")
    Optional<FamilyMembership> findByFamilyIdAndGuardianIdForUpdate(
            @Param("familyId") Long familyId, @Param("guardianId") Long guardianId);

    boolean existsByFamilyIdAndGuardianId(Long familyId, Long guardianId);

    boolean existsByFamilyIdAndGuardianIdAndIsLeaderTrue(Long familyId, Long guardianId);

    boolean existsByFamilyIdAndIsLeaderTrue(Long familyId);

    long countByFamilyId(Long familyId);

    // 가족 알림 대상 조회. 보호자 Member의 역방향 일대일도 함께 읽어 보호자마다 2건씩 더 나가지 않게 한다(LLD-0161).
    @Query("SELECT fm FROM FamilyMembership fm JOIN FETCH fm.guardian g " +
           "LEFT JOIN FETCH g.localAccount LEFT JOIN FETCH g.seniorProfile " +
           "WHERE fm.family.id = :familyId")
    List<FamilyMembership> findAllByFamilyIdWithGuardian(@Param("familyId") Long familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT fm FROM FamilyMembership fm WHERE fm.family.id = :familyId ORDER BY fm.id ASC")
    List<FamilyMembership> findAllByFamilyIdForUpdate(@Param("familyId") Long familyId);

    // Member의 역방향 일대일(localAccount, seniorProfile)은 EAGER라 함께 읽지 않으면 보호자마다 2건씩 더 나간다(LLD-0161).
    @Query("SELECT fm FROM FamilyMembership fm JOIN FETCH fm.guardian g " +
           "LEFT JOIN FETCH g.localAccount LEFT JOIN FETCH g.seniorProfile " +
           "WHERE fm.family.id = :familyId " +
           "ORDER BY fm.sortOrder ASC, fm.connectedAt ASC, fm.id ASC")
    List<FamilyMembership> findAllByFamilyIdWithGuardianOrdered(@Param("familyId") Long familyId);

    @Query("SELECT COALESCE(MAX(fm.sortOrder), -1) FROM FamilyMembership fm WHERE fm.family.id = :familyId")
    int findMaxSortOrderByFamilyId(@Param("familyId") Long familyId);

    @Query("SELECT COUNT(fm) > 0 FROM FamilyMembership fm " +
           "JOIN SeniorProfile sp ON sp.family.id = fm.family.id " +
           "WHERE fm.guardian.id = :guardianId AND sp.id = :seniorProfileId")
    boolean existsByGuardianIdAndSeniorProfileId(@Param("guardianId") Long guardianId,
                                                  @Param("seniorProfileId") Long seniorProfileId);

    @Query("SELECT COUNT(fm) > 0 FROM FamilyMembership fm " +
           "JOIN SeniorProfile sp ON sp.family.id = fm.family.id " +
           "WHERE fm.guardian.id = :guardianId AND sp.id = :seniorProfileId AND fm.isLeader = true")
    boolean existsByGuardianIdAndSeniorProfileIdAndIsLeaderTrue(@Param("guardianId") Long guardianId,
                                                                 @Param("seniorProfileId") Long seniorProfileId);

    /**
     * DB 장애 대비 긴급 수신자 스냅샷의 원천(LLD-0086 5.1). 시니어·보호자가 ACTIVE이고 활성 토큰이 있는
     * 보호자를 한 번에 읽는다. 안전 알림은 보호자 누구도 끌 수 없어 설정 행을 보지 않는다(LLD-0170,
     * {@code NotificationSettingService.isNotificationEnabled}와 같은 규칙). familyId가 null이면 전체다.
     */
    @Query("SELECT new com.widyu.member.repository.EmergencyRecipientRow(" +
           "sm.id, sm.name, sm.profileImage, g.id, t.id, t.token) " +
           "FROM SeniorProfile sp JOIN sp.member sm " +
           "JOIN FamilyMembership fm ON fm.family = sp.family " +
           "JOIN fm.guardian g " +
           "JOIN MemberFcmToken t ON t.member = g " +
           "WHERE sm.status = com.widyu.global.entity.Status.ACTIVE " +
           "AND g.status = com.widyu.global.entity.Status.ACTIVE " +
           "AND t.active = true " +
           "AND (:familyId IS NULL OR sp.family.id = :familyId)")
    List<EmergencyRecipientRow> findEmergencyRecipients(@Param("familyId") Long familyId);
}
