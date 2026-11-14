package com.widyu.location.stay.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;


import com.widyu.fcm.application.FcmOutboxService;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.fcm.dto.FcmSendDto;
import com.widyu.global.entity.Status;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.location.SeniorLocation;
import com.widyu.location.parentlocation.repository.ParentLocationRepository;
import com.widyu.location.realtime.application.LocationIngestService;
import com.widyu.location.realtime.application.LocationJudgeService;
import com.widyu.location.realtime.application.LocationMaintenanceService;
import com.widyu.location.realtime.application.LocationQueryService;
import com.widyu.location.realtime.dto.LocationUpdateRequest;
import com.widyu.location.realtime.dto.LocationUpdateResponse;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.stay.SafeZoneAlertState;
import com.widyu.location.stay.SeniorStayState;
import com.widyu.location.stay.repository.SeniorStayStateRepository;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.parentlocation.LocationType;
import com.widyu.parentlocation.ParentLocation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 체류, 안전구역 판정 상태가 알림 Outbox(알림 센터 행 포함)와 같은 MySQL 트랜잭션에서 커밋, 롤백되는지 본다(LLD-0141).
 * 실제 Spring 트랜잭션, JPA, 이벤트 리스너, Outbox 서비스를 쓰고 Redis 최신 위치 저장소만 대역이다(H2).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "firebase.config-path=/dev/null",
        "s3.credentials.access-key=test",
        "s3.credentials.secret-key=test",
        "s3.region.statics=ap-northeast-2",
        "s3.bucket-name=test-bucket",
        "coolsms.api-key=test",
        "coolsms.api-secret=test",
        "coolsms.api-url=https://api.coolsms.co.kr",
        "coolsms.from-phone-number=01000000000",
        "coolsms.verification-code-length=6",
        "coolsms.verification-code-ttl=300",
        "coolsms.message-template=인증번호: {code}"
})
@DisplayName("체류 상태와 알림 Outbox 원자성")
class StayStateOutboxAtomicityTest {

    private static final double HOME_LAT = 37.5665;
    private static final double HOME_LNG = 126.9780;
    private static final double NEAR_HOME_LAT = 37.5666; // 약 11m, 같은 체류
    private static final double AWAY_LAT = 37.5765; // 약 1.1km, 안심구역(75m) 밖
    private static final AtomicInteger SEQ = new AtomicInteger();

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;
    @MockBean private SeniorLocationRepository seniorLocationRepository;
    // 이동 경로 쓰기만 막는다. 빈을 통째로 대역으로 바꾸면 Redis 저장소 설정이 깨진다.
    @SpyBean(name = "redisTemplate") private RedisTemplate<String, Object> redisTemplate;
    @SpyBean private FcmOutboxService fcmOutboxService;

    @Autowired private LocationIngestService ingestService;
    @Autowired private LocationJudgeService judgeService;
    @Autowired private LocationQueryService queryService;
    @Autowired private LocationMaintenanceService maintenanceService;
    @Autowired private SeniorStayStateRepository stayStateRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private FamilyMembershipRepository familyMembershipRepository;
    @Autowired private ParentLocationRepository parentLocationRepository;
    @Autowired private JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void 이동_경로_쓰기를_막는다() {
        org.mockito.Mockito.doReturn(org.mockito.Mockito.mock(org.springframework.data.redis.core.ListOperations.class))
                .when(redisTemplate).opsForList();
        org.mockito.Mockito.doReturn(true).when(redisTemplate)
                .expire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("집에서 나갔다 돌아오면 상태 행에 묶음이 남고 이탈, 복귀 알림이 한 번씩 저장되며 같은 자리 위치는 행을 바꾸지 않는다")
    void 나갔다_돌아오면_상태와_알림이_한_번씩_저장된다() {
        // given
        Long seniorId = seniorWithGuardianAndHome();
        LocationUpdateResponse atHome = ingestService.applyRealtime(request(seniorId, HOME_LAT), seniorId);

        // when
        LocationUpdateResponse stillHome =
                ingestService.applyRealtime(request(seniorId, NEAR_HOME_LAT), seniorId);
        ingestService.applyRealtime(request(seniorId, AWAY_LAT), seniorId);
        LocationUpdateResponse back = ingestService.applyRealtime(request(seniorId, HOME_LAT), seniorId);

        // then
        assertThat(stillHome.stayStartTime()).isEqualTo(atHome.stayStartTime());
        assertThat(back.locationType()).isEqualTo("HOME");
        assertThat(alerts(seniorId, "SAFE_ZONE_EXITED")).isEqualTo(1);
        assertThat(alerts(seniorId, "SAFE_ZONE_ENTERED")).isEqualTo(1);
        SeniorStayState state = stayStateRepository.findById(seniorId).orElseThrow();
        assertThat(state.getAlertState()).isEqualTo(SafeZoneAlertState.RETURNED);
        assertThat(queryService.getOutingStatus(seniorId)).isFalse();
    }

    @Test
    @DisplayName("이탈 알림 저장이 실패해 트랜잭션이 롤백되면 상태도 남지 않고, 같은 위치를 다시 보내면 이탈과 알림이 함께 한 번 저장된다")
    void 알림_저장이_실패하면_상태도_롤백되고_재전송으로_복구된다() {
        // given
        Long seniorId = seniorWithGuardianAndHome();
        ingestService.applyRealtime(request(seniorId, HOME_LAT), seniorId);
        willThrow(new IllegalStateException("Outbox 저장 실패"))
                .willCallRealMethod()
                .given(fcmOutboxService).enqueue(any(), any(FcmSendDto.class));

        // when
        LocationUpdateRequest away = request(seniorId, AWAY_LAT);
        assertThatThrownBy(() -> ingestService.applyRealtime(away, seniorId))
                .isInstanceOf(IllegalStateException.class);
        SeniorStayState afterRollback = stayStateRepository.findById(seniorId).orElseThrow();
        int alertsAfterRollback = alerts(seniorId, "SAFE_ZONE_EXITED");
        ingestService.applyRealtime(away, seniorId);

        // then
        assertThat(afterRollback.getLocationType()).isEqualTo("HOME");
        assertThat(afterRollback.getAlertState()).isNull();
        assertThat(alertsAfterRollback).isZero();
        assertThat(alerts(seniorId, "SAFE_ZONE_EXITED")).isEqualTo(1);
        SeniorStayState recovered = stayStateRepository.findById(seniorId).orElseThrow();
        assertThat(recovered.getLocationType()).isNull();
        assertThat(recovered.getAlertState()).isEqualTo(SafeZoneAlertState.EXITED);
    }

    @Test
    @DisplayName("커밋 뒤 최신 위치 저장이 Redis 장애로 실패해도 상태와 알림은 남고, 같은 위치를 다시 보내면 알림을 다시 만들지 않는다")
    void 커밋_뒤_최신_위치_저장이_실패해도_재전송에서_알림이_중복되지_않는다() {
        // given
        Long seniorId = seniorWithGuardianAndHome();
        ingestService.applyRealtime(request(seniorId, HOME_LAT), seniorId);
        willThrow(new RedisConnectionFailureException("down"))
                .willReturn(null)
                .given(seniorLocationRepository).save(any(SeniorLocation.class));

        // when
        LocationUpdateRequest away = request(seniorId, AWAY_LAT);
        assertThatThrownBy(() -> ingestService.applyRealtime(away, seniorId))
                .isInstanceOf(RedisConnectionFailureException.class);
        ingestService.applyRealtime(away, seniorId);

        // then
        assertThat(alerts(seniorId, "SAFE_ZONE_EXITED")).isEqualTo(1);
        assertThat(stayStateRepository.findById(seniorId).orElseThrow().getAlertState())
                .isEqualTo(SafeZoneAlertState.EXITED);
    }

    @Test
    @DisplayName("회원 순서 잠금을 거치지 않는 두 판정(다른 인스턴스)이 같은 회원의 첫 상태를 동시에 만들어도 PK가 행 하나만 남기고 진 쪽은 상태와 알림을 남기지 않는다")
    void 첫_상태를_동시에_만들어도_행은_하나다() throws Exception {
        // given
        Long seniorId = seniorWithGuardianAndHome();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (double latitude : new double[]{HOME_LAT, AWAY_LAT}) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    judgeService.judge(request(seniorId, latitude), System.currentTimeMillis());
                    return "ok";
                } catch (RuntimeException e) {
                    return e.getClass().getSimpleName();
                }
            }));
        }

        // when
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // then
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM senior_stay_state WHERE member_id = ?", Integer.class, seniorId);
        assertThat(rows).isEqualTo(1);
        assertThat(outcomes).containsExactlyInAnyOrder("ok", "DataIntegrityViolationException");
        assertThat(alerts(seniorId, "SAFE_ZONE_EXITED") + alerts(seniorId, "SAFE_ZONE_ENTERED")).isZero();
    }

    @Test
    @DisplayName("활성이 아닌 회원은 최신 위치가 만료돼 없어도 TTL 갱신 때 체류 상태 행을 지우고 활성 회원 행은 남긴다")
    void 비활성_회원의_체류_상태는_최신_위치가_없어도_지운다() {
        // given
        Long inactive = seniorWithGuardianAndHome();
        Long active = seniorWithGuardianAndHome();
        ingestService.applyRealtime(request(inactive, HOME_LAT), inactive);
        ingestService.applyRealtime(request(active, HOME_LAT), active);
        jdbc.update("UPDATE member SET status = 'INACTIVE' WHERE id = ?", inactive);
        // 정리는 Redis 위치 키도 지운다. 실제 Redis 없이 돌도록 삭제만 대역으로 막는다.
        org.mockito.Mockito.doReturn(2L).when(redisTemplate).delete(org.mockito.ArgumentMatchers.anyCollection());

        // when
        maintenanceService.refreshLocationTtl();

        // then
        assertThat(stayStateRepository.existsById(inactive)).isFalse();
        assertThat(stayStateRepository.existsById(active)).isTrue();
    }

    private int alerts(Long seniorId, String type) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fcm_notification WHERE senior_id = ? AND type = ?",
                Integer.class, seniorId, type);
        return count;
    }

    private Long seniorWithGuardianAndHome() {
        int n = SEQ.incrementAndGet();
        Family family = familyRepository.save(Family.createFamily("SS" + String.format("%04d", n)));
        Member senior = memberRepository.save(
                Member.createMember(MemberType.SENIOR, "시니어" + n, "0197" + String.format("%07d", n)));
        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                senior, family, "서울", "SSI" + String.format("%04d", n), LocalDate.of(1950, 1, 1)));
        Member guardian = memberRepository.save(
                Member.createMember(MemberType.GUARDIAN, "보호자" + n, "0187" + String.format("%07d", n)));
        familyMembershipRepository.save(FamilyMembership.createMembership(family, guardian));
        parentLocationRepository.save(ParentLocation.builder()
                .member(senior).locationType(LocationType.HOME).placeAddress("서울")
                .latitude(HOME_LAT).longitude(HOME_LNG).name("집").status(Status.ACTIVE).build());
        return senior.getId();
    }

    private static LocationUpdateRequest request(Long memberId, double latitude) {
        return LocationUpdateRequest.of(memberId, latitude, HOME_LNG, null);
    }
}
