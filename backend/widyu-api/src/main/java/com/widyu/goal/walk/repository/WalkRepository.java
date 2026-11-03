package com.widyu.goal.walk.repository;

import com.widyu.member.Member;
import com.widyu.walk.Walk;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalkRepository extends JpaRepository<Walk, Long> {

    Optional<Walk> findByMemberAndWalkDate(Member member, LocalDate walkDate);

    /** 걸음 수 연동용. 같은 날 동기화가 겹치면 차례로 처리해 큰 값이 작은 값에 덮이지 않게 한다(#950). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Walk w WHERE w.member = :member AND w.walkDate = :walkDate")
    Optional<Walk> findByMemberAndWalkDateForUpdate(@Param("member") Member member,
                                                    @Param("walkDate") LocalDate walkDate);

    List<Walk> findByMemberAndWalkDateBetweenOrderByWalkDateAsc(
            Member member,
            LocalDate startDate,
            LocalDate endDate
    );

    @Query("SELECT COUNT(w) FROM Walk w " +
           "WHERE w.member.id = :memberId " +
           "AND w.walkDate BETWEEN :startDate AND :endDate " +
           "AND w.actualSteps >= w.goalSteps")
    long countAchievedGoals(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COUNT(w) FROM Walk w " +
           "WHERE w.member.id = :memberId " +
           "AND w.walkDate BETWEEN :startDate AND :endDate")
    long countTotalRecords(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    boolean existsByMemberAndWalkDate(Member member, LocalDate walkDate);

    Optional<Walk> findFirstByMemberOrderByWalkDateAsc(Member member);

    Optional<Walk> findFirstByMemberAndWalkDateAfterOrderByWalkDateAsc(Member member, LocalDate walkDate);

    @Query("SELECT w FROM Walk w " +
           "WHERE w.walkDate = :date " +
           "AND w.actualSteps < w.goalSteps")
    List<Walk> findUnachievedWalksByDate(@Param("date") LocalDate date);
}
