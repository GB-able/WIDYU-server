package com.widyu.admin.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> AdminPageResponse<T> from(Page<T> page) {
        return new AdminPageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
