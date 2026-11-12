package com.widyu.retention.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.widyu.retention.DataPurgeLog;
import com.widyu.retention.repository.DataPurgeLogRepository;
import com.widyu.retention.repository.RetentionPurgeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

@ExtendWith(MockitoExtension.class)
@DisplayName("보관 기간 경과 건수 기록 테스트")
class RetentionPurgeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 3, 30);

    @Mock private RetentionPurgeRepository retentionPurgeRepository;
    @Mock private DataPurgeLogRepository dataPurgeLogRepository;
    @InjectMocks private RetentionPurgeService retentionPurgeService;

    @Test
    @DisplayName("실행하면 대상 테이블마다 건수만 센 기록을 한 행씩 남긴다")
    void 실행하면_대상_테이블마다_건수만_센_기록을_한_행씩_남긴다() {
        // given
        given(retentionPurgeRepository.findBoundary(any(), any())).willReturn(100L);
        given(retentionPurgeRepository.countTargets(any(), eq(100L), any(), eq(NOW))).willReturn(7L);
        given(retentionPurgeRepository.countExempt(any(), eq(100L), any(), eq(NOW))).willReturn(2L);

        // when
        retentionPurgeService.countExpired(NOW);

        // then
        Map<String, DataPurgeLog> logs = savedLogs();
        assertThat(logs).hasSize(RetentionTarget.values().length);
        assertThat(logs.values()).allSatisfy(log -> {
            assertThat(log.isDryRun()).isTrue();
            assertThat(log.getTargetRows()).isEqualTo(7L);
            assertThat(log.getErrorType()).isNull();
        });
        assertThat(logs.get("heart_rate_event").getExemptRows()).isEqualTo(2L);
        assertThat(logs.get("heart_rate_event").getCutoffAt()).isEqualTo(NOW.minusDays(30));
        assertThat(logs.get("location_access_log").getExemptRows()).isNull();
        assertThat(logs.get("location_access_log").getCutoffAt()).isEqualTo(NOW.minusYears(1));
        then(retentionPurgeRepository).should(never())
                .countExempt(eq(RetentionTarget.LOCATION_ACCESS_LOG), anyLong(), any(), any());
    }

    @Test
    @DisplayName("한 테이블에서 예외가 나도 다음 테이블을 세고 실패한 테이블에 예외 종류를 남긴다")
    void 한_테이블에서_예외가_나도_다음_테이블을_세고_실패한_테이블에_예외_종류를_남긴다() {
        // given
        given(retentionPurgeRepository.findBoundary(any(), any())).willReturn(1L);
        given(retentionPurgeRepository.findBoundary(eq(RetentionTarget.SENSOR_BATCH), any()))
                .willThrow(new QueryTimeoutException("timeout"));
        given(retentionPurgeRepository.countTargets(any(), anyLong(), any(), any())).willReturn(0L);

        // when
        retentionPurgeService.countExpired(NOW);

        // then
        Map<String, DataPurgeLog> logs = savedLogs();
        assertThat(logs).hasSize(RetentionTarget.values().length);
        assertThat(logs.get("sensor_batch").getErrorType()).isEqualTo("QueryTimeoutException");
        assertThat(logs.get("sensor_batch").getTargetRows()).isNull();
        assertThat(logs.get("location_access_log").getErrorType()).isNull();
        assertThat(logs.get("location_access_log").getTargetRows()).isZero();
    }

    private Map<String, DataPurgeLog> savedLogs() {
        ArgumentCaptor<DataPurgeLog> captor = ArgumentCaptor.forClass(DataPurgeLog.class);
        then(dataPurgeLogRepository).should(times(RetentionTarget.values().length)).save(captor.capture());
        List<DataPurgeLog> logs = captor.getAllValues();
        assertThat(logs).extracting(DataPurgeLog::getRunId).containsOnly(logs.get(0).getRunId());
        return logs.stream().collect(Collectors.toMap(DataPurgeLog::getTarget, Function.identity()));
    }
}
