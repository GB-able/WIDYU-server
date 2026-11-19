package com.widyu.fcm;

import com.widyu.member.MemberType;
import java.util.stream.Stream;
import lombok.Getter;

/**
 * 알림 타입과 회원별 푸시 설정에 공통으로 쓰는 그룹.
 * 보호자 그룹은 위듀케어 알림 정책서 v18 2절 표의 스위치 14개이며 선언 순서가 화면 순서다(LLD-0175).
 */
@Getter
public enum PushSettingGroup {
    // 보호자: 위치
    SAFETY("심박 안전 알림", "LOCATION", true, true),
    SAFE_ZONE("안심구역", "LOCATION", true, false),
    SENIOR_OK_NOTICE("「본인은 괜찮다고 하셨어요」 안내", "LOCATION", false, false),
    // 보호자: 목표
    MEDICATION_CHECK("약 인증 확인", "GOAL", false, false),
    HEALTH_SCHEDULE("건강일정 임박", "GOAL", false, false),
    GOAL_ACHIEVED("목표 달성", "GOAL", false, false),
    // 보호자: 앨범
    ALBUM_NEW_POST("새 게시물", "ALBUM", false, false),
    ALBUM_COMMENT("댓글·답글", "ALBUM", false, false),
    ALBUM_LIKE("좋아요", "ALBUM", false, false),
    ALBUM_SENIOR_VIEWED("어르신 게시물 확인", "ALBUM", false, false),
    ALBUM_UPLOAD_COMPLETE("업로드 완료", "ALBUM", false, false),
    // 보호자: 메시지
    CHEER_MESSAGE("응원 메시지", "MESSAGE", false, false),
    // 보호자: 기타
    LEADER_CHANGED("가족 방장 변경", "ETC", false, false),
    SERVICE_NOTICE("운영 알림", "ETC", false, false),
    // 시니어: 켜짐 고정(위듀 알림 정책서 2절, LLD-0095)
    ALBUM("앨범", null, false, false),
    GOAL("목표", null, false, false),
    HOME("홈", null, false, false),
    ETC("기타", null, false, false),
    /** 14개로 나누기 전 보호자 「일반」. V4가 행을 옮기고 지운다. 남은 행이 읽기를 깨지 않도록 값만 둔다. */
    GENERAL("일반 알림", null, false, false),
    NONE("", null, false, false);

    private final String description;
    /** 보호자 설정 화면의 묶음. 알림센터 칩과 같다. 시니어 그룹과 GENERAL·NONE은 null. */
    private final String section;
    private final boolean mandatoryForLeader;
    /** 방장이 아니어도 보호자 누구도 끌 수 없다. 심박 안전 알림(위듀케어 알림 정책서 10절, LLD-0170). */
    private final boolean mandatoryForGuardians;

    PushSettingGroup(String description, String section, boolean mandatoryForLeader,
                     boolean mandatoryForGuardians) {
        this.description = description;
        this.section = section;
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
        return section != null;
    }

    /** 같은 회원 유형 설정 화면 안에서의 순서(1부터). 선언 순서를 따른다. */
    public int displayOrder() {
        MemberType audience = MemberType.GUARDIAN;
        if (section == null) {
            audience = MemberType.SENIOR;
        }
        MemberType target = audience;
        return (int) Stream.of(values())
                .filter(group -> group.visibleFor(target))
                .takeWhile(group -> group != this)
                .count() + 1;
    }

    /** type이 없는 기존 outbox의 전환기 폴백. null은 개인 설정을 적용하지 않는다. */
    public static PushSettingGroup fromLegacy(FcmCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case MEDICINE_SCHEDULE -> MEDICATION_CHECK;
            case SAFE_ZONE -> SAFE_ZONE;
            case ALBUM -> ALBUM_NEW_POST;
            case TARGET, WALK -> GOAL_ACHIEVED;
            case HEALTH_SCHEDULE -> HEALTH_SCHEDULE;
            case HEART_MESSAGE -> CHEER_MESSAGE;
            case ETC -> SERVICE_NOTICE;
            case ALL, UNREAD, INCIDENT_SELF_CHECK, LOCATION_NOTICE -> null;
        };
    }
}
