package com.widyu.fcm;

import com.widyu.member.MemberType;
import java.util.stream.Stream;
import lombok.Getter;

/** 알림 타입과 회원별 푸시 설정에 공통으로 쓰는 그룹. */
@Getter
public enum PushSettingGroup {
    SAFETY("안전 알림", true, true),
    SAFE_ZONE("안심구역", true, false),
    MEDICATION_CHECK("복약 확인", false, false),
    GENERAL("일반 알림", false, false),
    ALBUM("앨범", false, false),
    GOAL("목표", false, false),
    HOME("홈", false, false),
    ETC("기타", false, false),
    NONE("", false, false);

    private final String description;
    private final boolean mandatoryForLeader;
    /** 방장이 아니어도 보호자 누구도 끌 수 없다. 심박 안전 알림(위듀케어 알림 정책서 10절, LLD-0170). */
    private final boolean mandatoryForGuardians;

    PushSettingGroup(String description, boolean mandatoryForLeader, boolean mandatoryForGuardians) {
        this.description = description;
        this.mandatoryForLeader = mandatoryForLeader;
        this.mandatoryForGuardians = mandatoryForGuardians;
    }

    public static Stream<PushSettingGroup> stream() {
        return Stream.of(values()).filter(group -> group != NONE);
    }

    public boolean visibleFor(MemberType memberType) {
        if (memberType == MemberType.SENIOR) {
            return this == ALBUM || this == GOAL || this == HOME || this == ETC;
        }
        return this == SAFETY || this == SAFE_ZONE || this == MEDICATION_CHECK || this == GENERAL;
    }

    /** type이 없는 기존 outbox의 전환기 폴백. null은 개인 설정을 적용하지 않는다. */
    public static PushSettingGroup fromLegacy(FcmCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case MEDICINE_SCHEDULE -> MEDICATION_CHECK;
            case SAFE_ZONE -> SAFE_ZONE;
            case ALBUM, TARGET, HEALTH_SCHEDULE, WALK, HEART_MESSAGE, ETC -> GENERAL;
            case ALL, UNREAD, INCIDENT_SELF_CHECK, LOCATION_NOTICE -> null;
        };
    }
}
