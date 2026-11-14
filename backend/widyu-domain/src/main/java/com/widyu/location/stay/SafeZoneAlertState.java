package com.widyu.location.stay;

/** 안전구역 알림 묶음(30분) 안에서 마지막으로 보낸 알림. 묶음이 끝나면 의미가 없다(LLD-0141). */
public enum SafeZoneAlertState {
    /** 이탈 알림을 보냈다. 묶음 안에서 돌아오면 복귀 알림을 한 번 보낸다. */
    EXITED,
    /** 묶음 안에서 복귀 알림까지 보냈다. 묶음이 끝날 때까지 이탈, 복귀 알림을 더 보내지 않는다. */
    RETURNED
}
