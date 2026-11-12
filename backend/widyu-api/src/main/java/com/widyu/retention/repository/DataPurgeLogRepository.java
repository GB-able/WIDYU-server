package com.widyu.retention.repository;

import com.widyu.retention.DataPurgeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataPurgeLogRepository extends JpaRepository<DataPurgeLog, Long> {
}
