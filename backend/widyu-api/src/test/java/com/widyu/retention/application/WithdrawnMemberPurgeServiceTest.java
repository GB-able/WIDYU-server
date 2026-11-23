package com.widyu.retention.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.widyu.album.Album;
import com.widyu.album.AlbumComment;
import com.widyu.album.AlbumLike;
import com.widyu.global.config.JpaAuditingConfig;
import com.widyu.global.infrastructure.s3.deletion.S3ObjectDeletionTaskService;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateStatus;
import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.retention.DataPurgeLog;
import com.widyu.retention.repository.DataPurgeLogRepository;
import com.widyu.retention.repository.WithdrawnMemberPurgeRepository;
import com.widyu.run.CollectionRun;
import com.widyu.run.CollectionRunStatus;
import com.widyu.run.RunDeviceAssignment;
import com.widyu.run.RunExport;
import com.widyu.run.RunExportStatus;
import com.widyu.run.RunMarker;
import com.widyu.sensor.ClockMapping;
import com.widyu.study.StudyParticipation;
import com.widyu.study.StudyParticipationHistory;
import com.widyu.study.StudyParticipationHistoryType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, WithdrawnMemberPurgeService.class, WithdrawnMemberPurgeRepository.class})
@DisplayName("탈퇴 30일 뒤 회원 자료 삭제 테스트")
class WithdrawnMemberPurgeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 31, 3, 50);
    private static final String MEDIA_URL = "https://bucket.example/album/photo.jpg";
    private static final String EXPORT_KEY = "exports/STUDY-2026/run-due/run_run-due.zip";

    @Autowired private WithdrawnMemberPurgeService service;
    @Autowired private WithdrawnMemberPurgeRepository repository;
    @Autowired private DataPurgeLogRepository dataPurgeLogRepository;
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockBean private S3ObjectDeletionTaskService s3ObjectDeletionTaskService;

    @Test
    @DisplayName("모든 삭제 단계의 문장이 빈 테이블에서 0건을 지우고 기록 대상 이름이 칸 길이 안이다")
    void 모든_삭제_단계의_문장이_빈_테이블에서_0건을_지운다() {
        for (WithdrawnMemberPurgeStep step : WithdrawnMemberPurgeStep.values()) {
            assertThat(repository.delete(step, 1L)).as(step.name()).isZero();
            assertThat(step.logTarget()).as(step.name()).hasSizeLessThanOrEqualTo(40);
        }
    }

    @Test
    @DisplayName("30일이 지난 탈퇴 회원의 자료와 올린 앨범을 지우고 지운 건수를 남기며 다른 회원 자료는 남긴다")
    void 탈퇴_30일이_지난_회원의_자료와_앨범을_지운다() {
        // given
        Fixture fixture = fixture(false);

        // when
        service.purge(NOW);

        // then
        assertThat(albumsOf(fixture.due())).isZero();
        assertThat(albumsOf(fixture.other())).isEqualTo(1);
        assertThat(commentsOnAlbumsOf(fixture.due())).isZero();
        assertThat(rows("album_like")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM album_comment WHERE member_id = ?", String.class, fixture.due().getId()))
                .isEqualTo(WithdrawnMemberPurgeRepository.MASKED_COMMENT);
        assertThat(rowsOf("heart_rate_event", "member_id", fixture.due())).isZero();
        assertThat(rowsOf("location_access_log", "senior_member_id", fixture.due())).isZero();
        assertThat(rowsOf("location_access_log", "senior_member_id", fixture.recent())).isEqualTo(1);
        assertThat(purgedAt(fixture.due())).isEqualTo(NOW);
        assertThat(purgedAt(fixture.recent())).isNull();
        verify(s3ObjectDeletionTaskService).schedule(fixture.due().getId(), List.of(MEDIA_URL));
        assertThat(logRows("withdrawn.album")).isEqualTo(1L);
        assertThat(logRows("withdrawn.album_comment_reply")).isEqualTo(1L);
        assertThat(logRows("withdrawn.album_comment")).isEqualTo(1L);
        assertThat(logRows("withdrawn.album_comment_mask")).isEqualTo(1L);
        assertThat(logRows("withdrawn.heart_rate_event")).isEqualTo(1L);
        assertThat(dataPurgeLogRepository.findAll()).noneMatch(DataPurgeLog::isDryRun);
    }

    @Test
    @DisplayName("이미 지운 회원은 다음 실행에서 다시 고르지 않는다")
    void 이미_지운_회원은_다시_고르지_않는다() {
        // given
        Fixture fixture = fixture(false);
        service.purge(NOW);

        // when
        List<Long> due = repository.findDueMemberIds(NOW.minusDays(30), 100);

        // then
        assertThat(due).doesNotContain(fixture.due().getId());
    }

    @Test
    @DisplayName("실증 참가자도 연구 자료, 회차, 참여 기록과 내보내기 파일을 지우고 기기 시계 매핑은 남긴다")
    void 실증_참가자도_연구_자료와_회차와_참여_기록을_지운다() {
        // given
        Fixture fixture = fixture(true);

        // when
        service.purge(NOW);

        // then
        assertThat(rowsOf("heart_rate_event", "member_id", fixture.due())).isZero();
        assertThat(rowsOf("study_participation", "member_id", fixture.due())).isZero();
        assertThat(rowsOf("collection_run", "member_id", fixture.due())).isZero();
        assertThat(rows("study_participation_history")).isZero();
        assertThat(rows("run_device_assignment")).isZero();
        assertThat(rows("run_marker")).isZero();
        assertThat(rows("run_export")).isZero();
        assertThat(rows("clock_mapping")).isEqualTo(1);
        assertThat(purgedAt(fixture.due())).isEqualTo(NOW);
        verify(s3ObjectDeletionTaskService).scheduleKeys(fixture.due().getId(), List.of(EXPORT_KEY));
    }

    @Test
    @DisplayName("회차 내보내기를 만드는 중인 탈퇴 회원은 다음 실행으로 미룬다")
    void 회차_내보내기를_만드는_중인_회원은_미룬다() {
        // given
        Fixture fixture = fixture(true);
        jdbcTemplate.update("UPDATE run_export SET status = 'RUNNING', s3_key = NULL");

        // when
        service.purge(NOW);

        // then
        assertThat(albumsOf(fixture.due())).isEqualTo(1);
        assertThat(rows("run_export")).isEqualTo(1);
        assertThat(purgedAt(fixture.due())).isNull();
    }

    private Fixture fixture(boolean dueIsParticipant) {
        Member due = withdrawn("01020000001", NOW.minusDays(31));
        Member recent = withdrawn("01020000002", NOW.minusDays(10));
        Member other = entityManager.persist(Member.createMember(MemberType.GUARDIAN, "가족", "01020000003"));

        Album dueAlbum = entityManager.persist(
                Album.createAlbumWithMetadata(due, "사진", List.of(MEDIA_URL), List.of(), List.of()));
        AlbumComment otherComment = entityManager.persist(AlbumComment.createComment(dueAlbum, other, "좋아요"));
        entityManager.persist(AlbumComment.createReply(dueAlbum, due, otherComment, "고마워"));
        entityManager.persist(AlbumLike.createLike(dueAlbum, other));

        Album otherAlbum = entityManager.persist(
                Album.createAlbumWithMetadata(other, "가족", List.of(), List.of(), List.of()));
        AlbumComment dueComment = entityManager.persist(AlbumComment.createComment(otherAlbum, due, "보고 싶다"));
        entityManager.persist(AlbumComment.createReply(otherAlbum, other, dueComment, "저도요"));

        entityManager.persist(HeartRateEvent.of(due, 72, NOW.minusDays(40), HeartRateStatus.NORMAL, null, null));
        entityManager.persist(LocationAccessLog.of(other.getId(), due.getId(), LocationAccessPath.REST_LAST,
                NOW.minusDays(40)));
        entityManager.persist(LocationAccessLog.of(other.getId(), recent.getId(), LocationAccessPath.REST_LAST,
                NOW.minusDays(20)));
        if (dueIsParticipant) {
            persistStudyRun(due);
        }
        entityManager.flush();
        return new Fixture(due, recent, other);
    }

    private void persistStudyRun(Member member) {
        StudyParticipation participation = entityManager.persist(StudyParticipation.of("STUDY-2026", "part-due",
                member, "IRB-v1", LocalDate.of(2026, 9, 20), Map.of(), null, null, null, null));
        entityManager.persist(StudyParticipationHistory.snapshotOf(
                participation, StudyParticipationHistoryType.REGISTERED));
        CollectionRun run = entityManager.persist(CollectionRun.builder()
                .runId("run-due").member(member).participation(participation).studyId("STUDY-2026")
                .participationId("part-due").protocolRef("PROTO-1").consentVersion("v1").collectionMode("research")
                .startedAtMs(1_760_000_000_000L).status(CollectionRunStatus.CLOSED).build());
        entityManager.persist(RunDeviceAssignment.builder()
                .assignmentId("asg-due").run(run).deviceId("watch-1").role("WATCH").wearSite("LEFT_WRIST")
                .assignedAtMs(1_760_000_000_000L).build());
        entityManager.persist(ClockMapping.builder()
                .clockMappingId("cm-shared").deviceId("watch-1").bootId("boot-1").anchorElapsedNs(1L)
                .anchorEpochMs(1_760_000_000_000L).uncertaintyMs(1.0).observedMinElapsedNs(1L).observedMaxElapsedNs(1L)
                .firstSeenAtMs(1L).lastSeenAtMs(1L).build());
        entityManager.persist(RunMarker.builder()
                .markerId("mk-due").run(run).kind("START").sourceElapsedNs(1L).tsMs(1_760_000_000_000L).source("WATCH")
                .sourceDeviceId("watch-1").clockMappingId("cm-shared").build());
        RunExport export = RunExport.builder().exportId("exp-due").runId("run-due")
                .status(RunExportStatus.QUEUED).requestedAtMs(1L).serverBuild("test").build();
        export.finish(EXPORT_KEY, 10L, "0".repeat(64), 2L);
        entityManager.persist(export);
    }

    private Member withdrawn(String phoneNumber, LocalDateTime withdrawnAt) {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", phoneNumber);
        member.maskPersonalInfo();
        member.markWithdrawn(withdrawnAt);
        return entityManager.persist(member);
    }

    private long rows(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long rowsOf(String table, String column, Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Long.class, member.getId());
    }

    private long albumsOf(Member member) {
        return rowsOf("album", "member_id", member);
    }

    private long commentsOnAlbumsOf(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM album_comment c JOIN album a ON c.album_id = a.album_id WHERE a.member_id = ?",
                Long.class, member.getId());
    }

    private LocalDateTime purgedAt(Member member) {
        return jdbcTemplate.queryForObject(
                "SELECT personal_data_purged_at FROM member WHERE id = ?", LocalDateTime.class, member.getId());
    }

    private DataPurgeLog log(String target) {
        return dataPurgeLogRepository.findAll().stream()
                .filter(row -> row.getTarget().equals(target)).findFirst().orElseThrow();
    }

    private Long logRows(String target) {
        return log(target).getTargetRows();
    }

    private record Fixture(Member due, Member recent, Member other) {
    }
}
