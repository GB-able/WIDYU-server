package com.widyu.fcm.dto;

public final class GuardianDeepLinks {

    private GuardianDeepLinks() {
    }

    public static String post(String albumId) {
        return "/post?postId=" + albumId;
    }

    public static String postComment(String albumId, String commentId) {
        return post(albumId) + "&commentId=" + commentId;
    }

    public static String album() {
        return "/album";
    }

    public static String medicineGoal(Long seniorId) {
        return "/goal/medicine?seniorId=" + seniorId;
    }

    public static String walkGoal(Long seniorId) {
        return "/goal/walk?seniorId=" + seniorId;
    }

    public static String healthScheduleGoal(Long seniorId) {
        return "/goal/hospital?seniorId=" + seniorId;
    }

    public static String goal(Long seniorId) {
        return "/goal?seniorId=" + seniorId;
    }

    public static String notification() {
        return "/notification";
    }
}
