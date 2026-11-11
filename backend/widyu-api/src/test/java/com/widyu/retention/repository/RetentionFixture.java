package com.widyu.retention.repository;

import com.widyu.fcm.FcmNotification;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateStatus;
import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.location.raw.LocationFix;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.study.StudyParticipation;
import com.widyu.study.WithdrawalScope;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** 보관 기간 건수 조회 테스트의 행 고정값. 테스트마다 바꾸는 값만 인자로 받는다. */
final class RetentionFixture {

    private RetentionFixture() {
    }

    static Member senior(String phoneNumber) {
        return Member.createMember(MemberType.SENIOR, "시니어", phoneNumber);
    }

    static StudyParticipation activeParticipation(Member member, String participationId) {
        return StudyParticipation.of("STUDY-2026", participationId, member, "IRB-v1",
                LocalDate.of(2026, 9, 20), Map.of(), null, null, null, null);
    }

    static StudyParticipation deletionProcessedParticipation(Member member, String participationId) {
        StudyParticipation participation = activeParticipation(member, participationId);
        participation.withdraw(WithdrawalScope.ALL, null, LocalDateTime.of(2026, 10, 1, 0, 0));
        participation.markDeletionProcessed(LocalDateTime.of(2026, 10, 2, 0, 0));
        return participation;
    }

    static HeartRateEvent heartRate(Member member, LocalDateTime measuredAt) {
        return HeartRateEvent.of(member, 72, measuredAt, HeartRateStatus.NORMAL, null, null);
    }

    static LocationFix locationFix(Long memberId, long seq, String reason) {
        return LocationFix.builder()
                .memberId(memberId).deviceId("ph-9c1").sessionId("s-2026").seq(seq)
                .tsMs(1_760_000_000_000L + seq).lat(37.55).lon(126.97).isMock(false).reason(reason)
                .serverReceivedAtMs(1L).acceptedAtMs(1L).persistedAtMs(1L)
                .payload("{}").payloadSha256("0".repeat(64))
                .build();
    }

    static FcmNotification notification(String eventId, LocalDateTime expiresAt) {
        return FcmNotification.builder().eventId(eventId).title("알림").expiresAt(expiresAt).build();
    }

    static LocationAccessLog accessLog(LocalDateTime accessedAt) {
        return LocationAccessLog.of(1L, 2L, LocationAccessPath.REST_LAST, accessedAt);
    }
}
