package com.widyu.member.repository;

import com.widyu.member.ConnectionStatus;
import com.widyu.member.FamilyConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyConnectionRepository extends JpaRepository<FamilyConnection, Long> {

    List<FamilyConnection> findAllByGuardianId(Long guardianId);

    List<FamilyConnection> findAllByGuardianIdAndStatus(Long guardianId, ConnectionStatus status);

    List<FamilyConnection> findAllBySeniorId(Long seniorId);

    List<FamilyConnection> findAllBySeniorIdAndStatus(Long seniorId, ConnectionStatus status);

    Optional<FamilyConnection> findBySeniorIdAndGuardianId(Long seniorId, Long guardianId);

    boolean existsBySeniorIdAndGuardianId(Long seniorId, Long guardianId);

    @Query("SELECT fc FROM FamilyConnection fc " +
           "JOIN FETCH fc.senior sp " +
           "JOIN FETCH sp.member " +
           "WHERE fc.guardian.id = :guardianId AND fc.status = :status")
    List<FamilyConnection> findAllByGuardianIdWithSeniorAndMember(@Param("guardianId") Long guardianId,
                                                                    @Param("status") ConnectionStatus status);

    long countBySeniorIdAndStatus(Long seniorId, ConnectionStatus status);
}
