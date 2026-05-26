package com.widyu.admin.dto.response;

import java.time.LocalDateTime;

public record AdminLogEntryResponse(
        LocalDateTime time,
        String level,
        String logger,
        String message,
        String exception
) {}
