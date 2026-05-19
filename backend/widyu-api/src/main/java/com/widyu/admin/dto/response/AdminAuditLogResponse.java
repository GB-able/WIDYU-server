package com.widyu.admin.dto.response;

import com.widyu.admin.AdminAction;
import com.widyu.admin.AdminAuditLog;
import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        Long id,
        Long adminId,
        String adminName,
        AdminAction action,
        String targetType,
        Long targetId,
        String detail,
        LocalDateTime createdAt
) {
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdminId(),
                log.getAdminName(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getCreatedAt()
        );
    }
}
