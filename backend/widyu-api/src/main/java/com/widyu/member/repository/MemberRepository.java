package com.widyu.member.repository;

import com.widyu.global.entity.Status;
import com.widyu.member.Member;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("SELECT DISTINCT m FROM Member m JOIN FETCH m.socialAccounts sa "
            + "WHERE sa.provider = :provider AND sa.oauthId = :oauthId")
    Optional<Member> findByProviderAndOauthId(@Param("provider") String provider, @Param("oauthId") String oauthId);

    @Query("SELECT m.id FROM Member m JOIN m.socialAccounts sa "
            + "WHERE sa.provider = :provider AND sa.oauthId = :oauthId")
    Optional<Long> findMemberIdByProviderAndOauthId(@Param("provider") String provider, @Param("oauthId") String oauthId);

    @Query("SELECT DISTINCT m FROM Member m LEFT JOIN FETCH m.socialAccounts WHERE m.id = :id")
    Optional<Member> findWithAllAccountsById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    // 빈 번호일 때만 채운다. 확인과 갱신을 한 문장으로 해 동시 요청이 먼저 채운 번호를 덮어쓰지 않는다 (LLD-0094)
    @Modifying
    @Query("UPDATE Member m SET m.phoneNumber = :phoneNumber "
            + "WHERE m.id = :memberId AND (m.phoneNumber IS NULL OR TRIM(m.phoneNumber) = '')")
    int fillPhoneNumberIfEmpty(@Param("memberId") Long memberId, @Param("phoneNumber") String phoneNumber);

    // 소셜 계정을 다른 회원으로 옮긴다. 컬렉션에서 빼서 옮기면 orphanRemoval이 행을 지우므로 UPDATE 한 문장으로 바꾼다 (LLD-0157)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SocialAccount sa SET sa.member = :target "
            + "WHERE sa.member = :source AND sa.provider = :provider")
    int moveSocialAccount(@Param("source") Member source, @Param("target") Member target,
                          @Param("provider") String provider);

    // 애플 계정을 합치기 전에 빈 회원인지 본다. 회원 ID를 담는 컬럼(외래키 유무와 무관)이 있는 테이블 중
    // 시스템 행(알림, FCM, 로그인 계정, 삭제와 연동 해제 작업 큐, 동의 기록)과 관리자 기록(접속기록, 감사 로그)을 뺀 전부를 본다.
    // 하나라도 있으면 그 회원을 탈퇴 처리했을 때 기록에 닿을 수 없다 (LLD-0157)
    // ponytail: 목록을 손으로 관리한다. 회원 ID 컬럼을 가진 테이블이 새로 생기면 여기에 더한다
    @Query(value = "SELECT CASE WHEN "
            + "EXISTS (SELECT 1 FROM family_membership WHERE guardian_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM senior_profile WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM address_bookmark WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM album WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM album_comment WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM album_like WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM album_unlock WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM album_view WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM health_schedule WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM medicine_schedule WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM medication_proof WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM parent_location WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM walk WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM payment_order WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM payment WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM payment_cancel WHERE requested_by_member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM study_participation WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM collection_run WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM member_withdrawal_request WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM location_access_log WHERE viewer_member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM location_access_log WHERE senior_member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM location_fix WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM incident WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM incident WHERE guardian_response_by = :memberId) "
            + "OR EXISTS (SELECT 1 FROM incident WHERE resolved_by = :memberId) "
            + "OR EXISTS (SELECT 1 FROM incident_guardian_response WHERE guardian_member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM followup_card WHERE senior_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM decision_record WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM label_annotation WHERE reviewer_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM heart_rate_event WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM heart_rate_emergency WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM activity_window WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM sensor_batch WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM sensor_batch_rejection WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM sensor_dropped_range WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM device_heartbeat WHERE member_id = :memberId) "
            + "OR EXISTS (SELECT 1 FROM device_event WHERE member_id = :memberId) "
            + "THEN 1 ELSE 0 END", nativeQuery = true)
    int countOwnedContentFlag(@Param("memberId") Long memberId);

    Optional<Member> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<Member> findByPhoneNumberAndName(String phoneNumber, String name);
    Optional<Member> findByPhoneNumberAndNameAndLocalAccount_Email(String phoneNumber, String name, String email);
    Optional<Member> findBySocialAccounts_Email(String email);
    Optional<Member> findBySocialAccounts_EmailAndSocialAccounts_Provider(String email, String provider);
    long countByType(MemberType type);
    long countByCreatedAtAfter(LocalDateTime dateTime);
    long countByRoleNot(MemberRole role);
    long countByTypeAndRoleNot(MemberType type, MemberRole role);
    long countByCreatedAtAfterAndRoleNot(LocalDateTime dateTime, MemberRole role);
    long countByCreatedAtBetweenAndRoleNot(LocalDateTime start, LocalDateTime end, MemberRole role);
    List<Member> findTop20ByNameContainingOrderByIdDesc(String name);
    List<Member> findTop50ByOrderByIdDesc();
    Page<Member> findByNameContainingOrderByIdDesc(String name, Pageable pageable);
    Page<Member> findAllByOrderByIdDesc(Pageable pageable);
    List<Member> findTop3ByPhoneNumberContainingOrderByIdDesc(String phoneNumber);
    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    // 전화번호 유일 제약이 걸리기 전 기존 중복을 단건 조회 예외(500) 없이 알아채려고 두 건까지 읽는다 (LLD-0116)
    List<Member> findTop2ByPhoneNumber(String phoneNumber);
    List<Member> findTop2ByPhoneNumberAndName(String phoneNumber, String name);
    boolean existsByIdAndStatus(Long id, Status status);

    @Query("SELECT m.id FROM Member m WHERE m.id IN :ids AND m.status = com.widyu.global.entity.Status.ACTIVE")
    Set<Long> findActiveIdsIn(@Param("ids") Collection<Long> ids);
}
