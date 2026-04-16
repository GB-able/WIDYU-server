package com.widyu.member.repository;

import com.widyu.member.PointHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findAllBySeniorProfileIdOrderByCreatedAtDesc(Long seniorProfileId);
}
