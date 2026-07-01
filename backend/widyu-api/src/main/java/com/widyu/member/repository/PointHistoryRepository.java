package com.widyu.member.repository;

import com.widyu.member.PointHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findAllBySeniorProfileIdOrderByCreatedAtDesc(Long seniorProfileId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PointHistory p WHERE p.seniorProfile.id = :seniorProfileId")
    void deleteBySeniorProfileId(@Param("seniorProfileId") Long seniorProfileId);
}
