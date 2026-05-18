package com.widyu.member.repository;

import com.widyu.member.SeniorProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeniorProfileRepository extends JpaRepository<SeniorProfile, Long> {

    Optional<SeniorProfile> findByInviteCode(String inviteCode);

    Optional<SeniorProfile> findByMemberId(Long memberId);

    boolean existsByMemberId(Long memberId);

    boolean existsByInviteCode(String inviteCode);

    @Query("SELECT sp FROM SeniorProfile sp WHERE sp.inviteCode = :inviteCode AND sp.member.phoneNumber = :phoneNumber")
    Optional<SeniorProfile> findByInviteCodeAndMemberPhoneNumber(@Param("inviteCode") String inviteCode,
                                                                   @Param("phoneNumber") String phoneNumber);

    @Query("SELECT sp FROM SeniorProfile sp JOIN FETCH sp.member WHERE sp.family.id = :familyId")
    List<SeniorProfile> findAllByFamilyIdWithMember(@Param("familyId") Long familyId);

    List<SeniorProfile> findAllByFamilyId(Long familyId);
}
