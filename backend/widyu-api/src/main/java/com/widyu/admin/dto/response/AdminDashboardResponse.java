package com.widyu.admin.dto.response;

import java.util.List;

public record AdminDashboardResponse(
        // 회원
        long totalMembers,
        long seniorCount,
        long guardianCount,
        long todayNewMembers,
        long yesterdayNewMembers,

        // 가족 & 앨범
        long totalFamilyConnections,
        long todayAlbums,
        long weekAlbums,
        long monthAlbums,
        long processingAlbums,

        // 결제
        long todayPaymentTotal,
        long monthPaymentTotal,
        long pendingPayments,

        // 심박 응급
        long heartEmergencyCount,
        long todayHeartEmergencies,

        // 주간 추이 (최근 7일)
        List<DailyCount> weeklyMemberTrend,
        List<DailyCount> weeklyAlbumTrend
) {
    public record DailyCount(String date, long count) {}
}
