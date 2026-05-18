package com.widyu.admin.dto.response;

public record AdminDashboardResponse(
        long totalMembers,
        long seniorCount,
        long guardianCount,
        long todayNewMembers,
        long totalFamilyConnections,
        long todayAlbums,
        long weekAlbums,
        long monthAlbums,
        long todayPaymentTotal,
        long monthPaymentTotal,
        long heartEmergencyCount
) {}
