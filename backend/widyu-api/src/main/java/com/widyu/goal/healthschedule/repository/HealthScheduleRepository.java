package com.widyu.goal.healthschedule.repository;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import com.widyu.global.entity.Status;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthScheduleRepository extends JpaRepository<HealthSchedule, Long> {

    @Query("SELECT h FROM HealthSchedule h WHERE h.member.id = :memberId AND h.scheduledAt >= :startDate AND h.scheduledAt < :endDate")
    List<HealthSchedule> findByMemberIdAndYearMonth(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT h FROM HealthSchedule h WHERE h.member.id = :memberId AND DATE(h.scheduledAt) = :date")
    List<HealthSchedule> findByMemberIdAndDate(
            @Param("memberId") Long memberId,
            @Param("date") java.time.LocalDate date
    );

    @Query("SELECT h FROM HealthSchedule h WHERE h.member.id = :memberId AND h.scheduledAt >= :startDate AND h.scheduledAt < :endDate ORDER BY h.scheduledAt ASC")
    List<HealthSchedule> findByMemberIdAndWeek(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT h FROM HealthSchedule h WHERE h.progressStatus = :status AND h.scheduledAt >= :startDate AND h.scheduledAt < :endDate")
    List<HealthSchedule> findByStatusAndDateRange(
            @Param("status") ProgressStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}