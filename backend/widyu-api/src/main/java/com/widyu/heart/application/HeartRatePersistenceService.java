package com.widyu.heart.application;

import com.widyu.decision.DecisionRecord;
import com.widyu.decision.repository.DecisionRecordRepository;
import com.widyu.fcm.event.heart.dto.HeartRateEmergencyEvent;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.retry.RetryOnTransientLockFailure;
import com.widyu.heart.HeartRateEmergency;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.dto.request.HeartRateSingleRequest;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.heart.repository.HeartRateLatestResultWriter;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartRatePersistenceService {

    private final HeartRateLatestResultWriter latestResultWriter;
    private final HeartRateEventRepository heartRateEventRepository;
    private final HeartRateEmergencyRepository heartRateEmergencyRepository;
    private final MemberRepository memberRepository;
    private final DecisionRecordRepository decisionRecordRepository;
    private final HeartRateEmergencyNotificationService emergencyNotificationService;
    private final MeterRegistry meterRegistry;

    /**
     * 측정값 1건의 이벤트를 저장하고 최신값으로 쓸 결과를 돌려준다(LLD-0023). 락 충돌이면 새 트랜잭션으로 다시 시도한다(LLD-0076).
     * Redis 최신값은 여기서 쓰지 않는다. 호출자가 커밋 뒤 {@link #updateLatestResult}로 쓴다(LLD-0102).
     */
    @RetryOnTransientLockFailure
    @Transactional
    @Timed(value = "heart.persistence", extraTags = {"path", "single"})
    public HeartRateResult saveMeasurement(
            Long memberId,
            HeartRateSingleRequest request,
            HeartRateStatus status,
            boolean isEmergency
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        heartRateEventRepository.save(
                // 단건 경로에는 정확도도 배치 원문도 없다.
                HeartRateEvent.of(member, request.heartRate(), request.measuredAt(), status, null, null));

        if (isEmergency) {
            heartRateEmergencyRepository.save(HeartRateEmergency.of(
                    member, request.heartRate(), request.measuredAt(), request.location()));
        }

        return HeartRateResult.of(memberId, status, request.heartRate(), request.measuredAt());
    }

    /**
     * 배치 샘플 1건을 저장한다(LLD-0047 5절 6단계). 저장이 판정에 앞서므로 상태가 {@code UNKNOWN}이어도
     * 그대로 저장한다. 위급 사건은 호출자가 커밋 뒤 {@link #notifyEmergency}로 연다.
     * 배치에는 주소가 없어 {@code location}은 null이다(ADR-0031 후속).
     *
     * <p>{@code decision}은 이 위급을 낳은 판정 기록이며 <b>여기서 같은 트랜잭션으로 저장한다</b>.
     * 판정 행을 먼저 따로 커밋하면 이어지는 심박 저장이 실패했을 때 심박 이벤트도 위급 알림도 없이
     * 판정만 남아, 「알림이 갔다고 적혔지만 아무것도 가지 않은」 자료가 생긴다. 판정이 없으면 null이다.
     *
     * <p>락 충돌이면 새 트랜잭션으로 다시 시도한다(LLD-0076). 알림은 커밋 뒤에 열므로 실패한 시도의 알림은 없다.
     */
    @RetryOnTransientLockFailure
    @Transactional
    @Timed(value = "heart.persistence", extraTags = {"path", "batch"})
    public void saveBatchSample(
            Member member,
            Integer heartRate,
            LocalDateTime measuredAt,
            HeartRateStatus status,
            boolean isEmergency,
            String accuracy,
            String batchId,
            DecisionRecord decision
    ) {
        if (decision != null) {
            decisionRecordRepository.save(decision);
        }

        heartRateEventRepository.save(
                HeartRateEvent.of(member, heartRate, measuredAt, status, accuracy, batchId));

        if (isEmergency) {
            heartRateEmergencyRepository.save(
                    HeartRateEmergency.of(member, heartRate, measuredAt, null));
        }
    }

    /**
     * 저장이 커밋된 위급으로 사건을 열고 보호자 최초 알림을 낸다. 단건, 배치 모두 심박 저장 트랜잭션이 끝난 뒤
     * 트랜잭션 없이 부른다(LLD-0160). 커밋 후 리스너로 열면 저장 트랜잭션의 커넥션을 돌려주기 전에 사건 트랜잭션이
     * 커넥션을 하나 더 잡아, 동시 위급이 풀을 넘으면 사건이 열리지 않고 ERROR 로그만 남았다.
     *
     * <p>{@code decision}은 이 위급을 낳은 판정 기록이다. 전송이 성공하면 그 행에 알림 도달 사실을 채운다
     * (LLD-0053 5.2). 판정 기록을 남기지 않는 단건 경로에서는 null이다.
     *
     * <p>실패는 이전 리스너처럼 호출자에게 전파하지 않는다. 심박은 이미 저장됐고, 던지면 단건은 DB 장애로 오인돼
     * 저하 경로로 가고 배치는 재전송이 반복된다.
     */
    public void notifyEmergency(Long memberId, DecisionRecord decision) {
        String decisionId = null;
        if (decision != null) {
            decisionId = decision.getDecisionId();
        }
        try {
            emergencyNotificationService.handleHeartRateEmergency(new HeartRateEmergencyEvent(memberId, decisionId));
        } catch (RuntimeException e) {
            log.error("심박 위급 사건 열기 실패, 심박 저장은 유지: memberId={}", memberId, e);
        }
    }

    /**
     * 저장이 커밋된 측정으로 최신값(Redis)을 갱신한다. 저장된 값보다 늦은 측정일 때만 덮어쓴다(LLD-0090).
     *
     * <p>단건·배치 모두 심박 저장 트랜잭션이 끝난 뒤 트랜잭션 없이 부른다. 최신값은 이력에서 다시 만들 수 있는
     * 조회용 사본이라 Redis가 실패해도 WARN과 메트릭만 남기고 예외를 내지 않는다(LLD-0102). 던지면 단건은 DB 장애로
     * 오인돼 저하 경로로 가고, 배치는 인덱스 행을 못 만들어 재전송이 반복된다. 심박 값·측정 시각은 로그에 남기지 않는다(#639).
     */
    public void updateLatestResult(
            Long memberId, HeartRateStatus status, Integer heartRate, LocalDateTime measuredAt) {
        try {
            latestResultWriter.saveIfNewer(HeartRateResult.of(memberId, status, heartRate, measuredAt));
        } catch (RuntimeException e) {
            meterRegistry.counter("heart.latest.write.failed").increment();
            log.warn("최신 심박 갱신 실패, 저장·알림은 유지: memberId={}, type={}",
                    memberId, e.getClass().getSimpleName());
        }
    }
}
