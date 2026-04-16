package com.widyu.member.repository;

import com.widyu.member.FamilyConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyConnectionRepository extends JpaRepository<FamilyConnection, Long> {

    List<FamilyConnection> findAllByGuardianId(Long guardianId);

    List<FamilyConnection> findAllBySeniorId(Long seniorId);

    Optional<FamilyConnection> findBySeniorIdAndGuardianId(Long seniorId, Long guardianId);

    boolean existsBySeniorIdAndGuardianId(Long seniorId, Long guardianId);

    @Query("SELECT fc FROM FamilyConnection fc " +
           "JOIN FETCH fc.senior sp " +
           "JOIN FETCH sp.member " +
           "WHERE fc.guardian.id = :guardianId")
    List<FamilyConnection> findAllByGuardianIdWithSeniorAndMember(@Param("guardianId") Long guardianId);

    long countBySeniorId(Long seniorId);

    @Query("SELECT fc FROM FamilyConnection fc JOIN FETCH fc.guardian WHERE fc.senior.id = :seniorId")
    List<FamilyConnection> findAllBySeniorIdWithGuardian(@Param("seniorId") Long seniorId);

    Optional<FamilyConnection> findBySeniorIdAndIsRepresentativeTrue(Long seniorId);

    Optional<FamilyConnection> findBySeniorIdAndGuardianIdAndIsLeaderTrue(Long seniorId, Long guardianId);

    boolean existsBySeniorIdAndGuardianIdAndIsLeaderTrue(Long seniorId, Long guardianId);
}
