package com.widyu.member.integration;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.application.FamilyAccessService;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 위치 방송의 보호자 인가 조회가 커넥션을 쥔 채 남지 않는지 커넥션 1개 풀로 확인한다(LLD-0140).
 *
 * <p>위치 처리 메서드는 트랜잭션 없이(NOT_SUPPORTED) 돈다. 그 안에서 보낸 방송의 인가 조회(FamilyTopicOutboundInterceptor →
 * FamilyAccessService)가 트랜잭션 없는 조회로 커넥션을 받으면, 메서드가 끝날 때까지 놓지 않는다. 이어서 원본 저장
 * (REQUIRES_NEW)이 두 번째 커넥션을 요청하면 풀이 1개일 때는 반드시, 10개일 때는 동시 요청이 10건을 넘으면 막힌다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=1000",
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
@DisplayName("방송 인가 조회의 커넥션 점유")
class FamilyAccessConnectionHoldTest {

    @MockBean private FcmTransport transport;
    @MockBean private S3Service s3Service;
    @MockBean private S3Client s3Client;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @Autowired private FamilyAccessService familyAccessService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private FamilyMembershipRepository familyMembershipRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("트랜잭션 없는 위치 처리 안에서 보호자 인가를 확인한 뒤에도 원본 저장이 새 커넥션을 얻는다")
    void 인가_확인_뒤에도_원본_저장이_커넥션을_얻는다() {
        // given
        Family family = familyRepository.save(Family.createFamily("CH0001"));
        Member senior = memberRepository.save(Member.createMember(MemberType.SENIOR, "시니어", "01900000001"));
        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                senior, family, "서울", "CHI0001", LocalDate.of(1950, 1, 1)));
        Member guardian = memberRepository.save(Member.createMember(MemberType.GUARDIAN, "보호자", "01800000001"));
        familyMembershipRepository.save(FamilyMembership.createMembership(family, guardian));
        TransactionTemplate notSupported = new TransactionTemplate(transactionManager);
        notSupported.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when & then
        assertThatCode(() -> notSupported.executeWithoutResult(status -> {
            familyAccessService.verifyActiveFamilyAccess(guardian.getId(), senior.getId());
            requiresNew.executeWithoutResult(inner -> memberRepository.count());
        })).doesNotThrowAnyException();
    }
}
