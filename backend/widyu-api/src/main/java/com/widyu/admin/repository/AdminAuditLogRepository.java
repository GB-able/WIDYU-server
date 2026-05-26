package com.widyu.admin.repository;

import com.widyu.admin.AdminAction;
import com.widyu.admin.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Page<AdminAuditLog> findAllByOrderByIdDesc(Pageable pageable);

    Page<AdminAuditLog> findByActionOrderByIdDesc(AdminAction action, Pageable pageable);
}
