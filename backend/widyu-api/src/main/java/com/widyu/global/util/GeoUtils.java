package com.widyu.global.util;

public class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000;

    private GeoUtils() {
    }

    /**
     * Haversine 공식을 사용하여 두 좌표 간 거리(미터) 계산
     */
    public static double calculateDistanceInMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * 두 위치가 지정된 반경(미터) 내에 있는지 확인
     */
    public static boolean isWithinRadius(double lat1, double lng1, double lat2, double lng2, double radiusMeters) {
        return calculateDistanceInMeters(lat1, lng1, lat2, lng2) <= radiusMeters;
    }
}
