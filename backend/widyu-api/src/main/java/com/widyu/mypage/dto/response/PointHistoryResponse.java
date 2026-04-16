package com.widyu.mypage.dto.response;

import com.widyu.member.PointHistory;
import com.widyu.member.PointHistoryType;
import java.time.LocalDateTime;
import java.util.List;

public record PointHistoryResponse(
        Long currentPoints,
        List<PointHistoryItem> histories
) {
    public record PointHistoryItem(
            PointHistoryType type,
            Long amount,
            String description,
            LocalDateTime createdAt
    ) {
        public static PointHistoryItem from(PointHistory pointHistory) {
            return new PointHistoryItem(
                    pointHistory.getType(),
                    pointHistory.getAmount(),
                    pointHistory.getDescription(),
                    pointHistory.getCreatedAt()
            );
        }
    }

    public static PointHistoryResponse of(Long currentPoints, List<PointHistory> histories) {
        return new PointHistoryResponse(
                currentPoints,
                histories.stream().map(PointHistoryItem::from).toList()
        );
    }
}
