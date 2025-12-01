package com.widyu.goal.medicineschedule.repository;

import com.widyu.global.entity.Status;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineScheduleRepository extends JpaRepository<MedicineSchedule, Long> {

    @Query("SELECT ms FROM MedicineSchedule ms " +
           "WHERE ms.member = :member AND ms.status = :status " +
           "ORDER BY ms.alarmTime ASC")
    List<MedicineSchedule> findByMemberAndStatusOrderByAlarmTime(
            @Param("member") Member member,
            @Param("status") Status status
    );

    @Query("SELECT DISTINCT ms FROM MedicineSchedule ms " +
           "LEFT JOIN FETCH ms.categories c " +
           "WHERE ms.id = :scheduleId AND ms.status = :status")
    Optional<MedicineSchedule> findByIdAndStatusWithDetails(
            @Param("scheduleId") Long scheduleId,
            @Param("status") Status status
    );

    @Query("SELECT DISTINCT ms FROM MedicineSchedule ms " +
           "LEFT JOIN FETCH ms.categories c " +
           "WHERE ms.member = :member AND ms.status = :status " +
           "ORDER BY ms.alarmTime ASC")
    List<MedicineSchedule> findByMemberAndStatusWithDetails(
            @Param("member") Member member,
            @Param("status") Status status
    );

    long countByMemberAndStatus(Member member, Status status);
}
