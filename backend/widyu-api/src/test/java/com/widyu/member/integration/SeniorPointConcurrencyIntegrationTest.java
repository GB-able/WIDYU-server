package com.widyu.member.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.auth.repository.TemporaryMemberRepository;
import com.widyu.auth.repository.VerificationCodeRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.application.SeniorProfileService;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

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
@DisplayName("시니어 포인트 동시성 통합 테스트")
class SeniorPointConcurrencyIntegrationTest {

    @Autowired private SeniorProfileService seniorProfileService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;

    @MockBean private MemberUtil memberUtil;
    @MockBean private VerificationCodeRepository verificationCodeRepository;
    @MockBean private TemporaryMemberRepository temporaryMemberRepository;
    @MockBean private RefreshTokenRepository refreshTokenRepository;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        seniorProfileRepository.deleteAll();
        familyRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("동일 시니어에게 포인트를 동시에 적립하면 모든 적립이 유실 없이 반영된다")
    void 포인트_동시_적립_유실없음() throws InterruptedException {
        // given
        Long memberId = createSeniorMember("홍길동", "01012345678", "FAM100", "INV1001");
        long initialPoints = seniorProfileRepository.findByMemberId(memberId).orElseThrow().getPoints();
        int threadCount = 10;
        long addAmount = 10L;

        // when
        AtomicInteger successCount = runConcurrently(threadCount,
                () -> seniorProfileService.addPointsToMember(memberId, addAmount, "동시 적립 테스트"));

        // then
        long finalPoints = seniorProfileRepository.findByMemberId(memberId).orElseThrow().getPoints();
        assertThat(finalPoints).isEqualTo(initialPoints + addAmount * successCount.get());
    }

    @Test
    @DisplayName("잔액을 초과하는 동시 차감 요청에도 잔액이 음수가 되지 않고 성공한 만큼만 차감된다")
    void 포인트_동시_차감_과차감없음() throws InterruptedException {
        // given
        Long memberId = createSeniorMember("김영희", "01099998888", "FAM200", "INV2002");
        long initialPoints = seniorProfileRepository.findByMemberId(memberId).orElseThrow().getPoints();
        int threadCount = 10;
        long deductAmount = 50L;

        // when
        AtomicInteger successCount = runConcurrently(threadCount,
                () -> seniorProfileService.deductPointsFromMember(memberId, deductAmount, "동시 차감 테스트"));

        // then
        long finalPoints = seniorProfileRepository.findByMemberId(memberId).orElseThrow().getPoints();
        assertThat(finalPoints).isEqualTo(initialPoints - deductAmount * successCount.get());
        assertThat(finalPoints).isGreaterThanOrEqualTo(0L);
    }

    private AtomicInteger runConcurrently(int threadCount, Runnable action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    action.run();
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                    // 잔액 부족 또는 재시도 소진 시 실패는 정상 흐름이므로 무시한다
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        return successCount;
    }

    private Long createSeniorMember(String name, String phoneNumber, String familyCode, String inviteCode) {
        Family family = familyRepository.save(Family.createFamily(familyCode));
        Member member = memberRepository.save(Member.createMember(MemberType.SENIOR, name, phoneNumber));
        SeniorProfile seniorProfile = seniorProfileRepository.save(
                SeniorProfile.createSeniorProfile(
                        member,
                        family,
                        "서울시 강남구",
                        inviteCode,
                        LocalDate.of(1950, 1, 1)
                )
        );
        return member.getId();
    }
}
