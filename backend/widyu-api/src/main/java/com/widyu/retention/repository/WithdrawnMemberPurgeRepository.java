package com.widyu.retention.repository;

import com.widyu.retention.application.WithdrawnMemberPurgeStep;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 탈퇴 30일 뒤 회원 자료 삭제(LLD-0187). 표와 조건은 {@link WithdrawnMemberPurgeStep}에서만 온다. */
@Repository
@RequiredArgsConstructor
public class WithdrawnMemberPurgeRepository {

    /** 다른 가족 앨범에 단 댓글은 답글이 매달려 있어 지우지 않고 내용만 가린다(LLD-0187 5.3). */
    public static final String MASKED_COMMENT = "탈퇴한 회원의 댓글입니다.";

    private final NamedParameterJdbcTemplate jdbc;

    /** 탈퇴 시각이 컷오프보다 앞서고 아직 지우지 않은 회원. 관리자 정지는 탈퇴 시각이 없어 고르지 않는다. */
    public List<Long> findDueMemberIds(LocalDateTime cutoff, int limit) {
        return jdbc.queryForList(
                "SELECT id FROM member WHERE withdrawn_at < :cutoff AND personal_data_purged_at IS NULL"
                        + " ORDER BY id LIMIT :limit",
                new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("limit", limit),
                Long.class);
    }

    /** 연구 자료 수동 삭제를 마치지 않은 실증 참가자인가(ADR-0043 결정 4). */
    public boolean isResearchParticipant(Long memberId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM study_participation"
                        + " WHERE member_id = :memberId AND deletion_processed_at IS NULL",
                member(memberId), Long.class);
        return count > 0;
    }

    public long count(WithdrawnMemberPurgeStep step, Long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + step.table() + " WHERE " + step.condition(), member(memberId), Long.class);
    }

    public long delete(WithdrawnMemberPurgeStep step, Long memberId) {
        return jdbc.update("DELETE FROM " + step.table() + " WHERE " + step.condition(), member(memberId));
    }

    /** 탈퇴자가 올린 앨범의 사진, 영상, 썸네일 주소. 행을 지우기 전에 읽는다. */
    public List<String> albumFileUrls(Long memberId) {
        return jdbc.queryForList(
                "SELECT u.media_url FROM album_media_url u JOIN album a ON u.album_id = a.album_id"
                        + " WHERE a.member_id = :memberId AND u.media_url IS NOT NULL"
                        + " UNION ALL SELECT t.thumbnail_url FROM album_thumbnail_url t"
                        + " JOIN album a ON t.album_id = a.album_id"
                        + " WHERE a.member_id = :memberId AND t.thumbnail_url IS NOT NULL",
                member(memberId), String.class);
    }

    /** 탈퇴 때 지웠어야 할 복약 인증 사진이 남아 있으면 함께 지운다(LLD-0030). */
    public List<String> medicationProofImageUrls(Long memberId) {
        return jdbc.queryForList(
                "SELECT i.image_url FROM medication_proof_images i JOIN medication_proof p"
                        + " ON i.medication_proof_id = p.id WHERE p.member_id = :memberId AND i.image_url IS NOT NULL",
                member(memberId), String.class);
    }

    /** 센서 원문 S3 키. 인덱스 행이 객체의 유일한 목록이라 행보다 먼저 읽는다(ADR-0030 결정 1). */
    public List<String> sensorObjectKeys(Long memberId) {
        return jdbc.queryForList(
                "SELECT s3_key FROM sensor_batch WHERE member_id = :memberId AND s3_key IS NOT NULL",
                member(memberId), String.class);
    }

    public long countCommentsToMask(Long memberId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM album_comment WHERE member_id = :memberId AND content <> :masked",
                member(memberId).addValue("masked", MASKED_COMMENT), Long.class);
    }

    public long maskComments(Long memberId) {
        return jdbc.update(
                "UPDATE album_comment SET content = :masked WHERE member_id = :memberId AND content <> :masked",
                member(memberId).addValue("masked", MASKED_COMMENT));
    }

    public void markPurged(Long memberId, LocalDateTime now) {
        jdbc.update("UPDATE member SET personal_data_purged_at = :now WHERE id = :memberId",
                member(memberId).addValue("now", now));
    }

    private static MapSqlParameterSource member(Long memberId) {
        return new MapSqlParameterSource(Map.of("memberId", memberId));
    }
}
