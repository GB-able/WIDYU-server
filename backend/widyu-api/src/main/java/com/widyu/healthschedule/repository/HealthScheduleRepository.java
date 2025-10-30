package com.widyu.healthschedule.repository;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.global.entity.Status;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthScheduleRepository extends JpaRepository<HealthSchedule, Long> {
}