package com.widyu.member.repository;

import com.widyu.member.ParentProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, Long> {
    Optional<ParentProfile> findByInviteCodeAndMemberPhoneNumber(String inviteCode, String phoneNumber);
    List<ParentProfile> findAllByGuardianId(Long guardianId);
    Optional<ParentProfile> findByMemberId(Long memberId);
    boolean existsByMemberId(Long memberId);
}
