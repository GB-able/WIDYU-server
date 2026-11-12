package com.widyu.incident.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.incident.IncidentKind;
import com.widyu.incident.IncidentOutcome;
import com.widyu.incident.IncidentResponseValue;
import com.widyu.incident.IncidentState;
import com.widyu.incident.ResponseVia;
import com.widyu.incident.repository.IncidentAlertRecordRepository;
import com.widyu.incident.repository.IncidentAlertRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentAlertExportService 알림 기록 CSV 테스트")
class IncidentAlertExportServiceTest {

    // 2026-11-01T00:00:00+09:00
    private static final long FROM_MS = 1_793_458_800_000L;
    private static final long OPENED_AT_MS = FROM_MS + 1_000L;
    private static final long RESPOND_BY_MS = OPENED_AT_MS + 90_000L;
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 1, 0, 0);

    @Mock private IncidentAlertRecordRepository incidentAlertRecordRepository;
    @InjectMocks private IncidentAlertExportService incidentAlertExportService;

    @Test
    @DisplayName("기간을 주면 한국 시간을 epoch ms로 바꿔 조회하고 알림마다 한 줄씩 내보낸다")
    void 기간을_주면_알림마다_한_줄씩_내보낸다() {
        // given
        IncidentAlertRow withEvidence = row(IncidentResponseValue.OK, OPENED_AT_MS + 30_000L, 185, "서맥", true, 1_500L);
        IncidentAlertRow product = row(null, null, null, null, null, null);
        given(incidentAlertRecordRepository.findAlertRecords(eq(FROM_MS), eq(FROM_MS + 86_400_000L), anyList()))
                .willReturn(List.of(withEvidence, product));

        // when
        String csv = new String(incidentAlertExportService.export(FROM, FROM.plusDays(1)), UTF_8);

        // then
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo(IncidentAlertExportService.HEADER.strip());
        assertThat(lines[1]).isEqualTo("2026-11-01 00:00:01.000," + OPENED_AT_MS + ",inc-1,28,HR_ANOMALY,run-01,1,dec-1,"
                + "EMERGENCY,185," + OPENED_AT_MS + ",HIGH,서맥,true,WORN_VALID,,,," + RESPOND_BY_MS + ",OK,OK,"
                + (OPENED_AT_MS + 30_000L) + ",WATCH,OK_CLOSED," + OPENED_AT_MS + ",true," + (OPENED_AT_MS + 1_500L)
                + ",FALSE_ALARM");
        assertThat(lines[2]).isEqualTo("2026-11-01 00:00:01.000," + OPENED_AT_MS + ",inc-1,28,HR_ANOMALY,run-01,1,dec-1,"
                + "EMERGENCY,,,,,,,,,," + RESPOND_BY_MS + ",NO_RESPONSE,,,WATCH,OK_CLOSED," + OPENED_AT_MS + ",,,"
                + "FALSE_ALARM");
    }

    @Test
    @DisplayName("기간이 31일을 넘으면 내보내기 시간대 예외가 발생한다")
    void 기간이_31일을_넘으면_예외가_발생한다() {
        assertThatThrownBy(() -> incidentAlertExportService.export(FROM, FROM.plusDays(31).plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SENSOR_EXPORT_RANGE_INVALID);
    }

    @Test
    @DisplayName("시작이 끝과 같으면 내보내기 시간대 예외가 발생한다")
    void 시작이_끝과_같으면_예외가_발생한다() {
        assertThatThrownBy(() -> incidentAlertExportService.export(FROM, FROM))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SENSOR_EXPORT_RANGE_INVALID);
    }

    @Test
    @DisplayName("마감 전 괜찮아요는 OK, 도움 요청은 HELP로 계산한다")
    void 마감_전_괜찮아요는_OK_도움_요청은_HELP다() {
        // given
        IncidentAlertRow ok = row(IncidentResponseValue.OK, RESPOND_BY_MS - 1L, null, null, null, null);
        IncidentAlertRow help = row(IncidentResponseValue.HELP, RESPOND_BY_MS + 5_000L, null, null, null, null);

        // when
        String okResult = IncidentAlertExportService.seniorResponse(ok, RESPOND_BY_MS + 10_000L);
        String helpResult = IncidentAlertExportService.seniorResponse(help, RESPOND_BY_MS + 10_000L);

        // then
        assertThat(okResult).isEqualTo("OK");
        assertThat(helpResult).isEqualTo("HELP");
    }

    @Test
    @DisplayName("마감과 같은 시각의 괜찮아요와 마감 뒤 응답 없음은 NO_RESPONSE로 계산한다")
    void 마감과_같은_괜찮아요와_마감_뒤_응답_없음은_NO_RESPONSE다() {
        // given
        IncidentAlertRow lateOk = row(IncidentResponseValue.OK, RESPOND_BY_MS, null, null, null, null);
        IncidentAlertRow silent = row(null, null, null, null, null, null);

        // when
        String lateOkResult = IncidentAlertExportService.seniorResponse(lateOk, RESPOND_BY_MS + 10_000L);
        String silentResult = IncidentAlertExportService.seniorResponse(silent, RESPOND_BY_MS);

        // then
        assertThat(lateOkResult).isEqualTo("NO_RESPONSE");
        assertThat(silentResult).isEqualTo("NO_RESPONSE");
    }

    @Test
    @DisplayName("마감 전이고 응답이 없으면 PENDING으로 계산한다")
    void 마감_전이고_응답이_없으면_PENDING이다() {
        // given
        IncidentAlertRow waiting = row(null, null, null, null, null, null);

        // when
        String result = IncidentAlertExportService.seniorResponse(waiting, RESPOND_BY_MS - 1L);

        // then
        assertThat(result).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("수식처럼 시작하거나 쉼표가 든 사유를 내보내면 작은따옴표를 붙이고 따옴표로 감싼다")
    void 수식처럼_시작하거나_쉼표가_든_사유는_감싼다() {
        // given
        IncidentAlertRow formula = row(null, null, 185, "=HYPERLINK(\"x\"),1", null, null);

        // when
        String line = IncidentAlertExportService.row(formula, RESPOND_BY_MS);

        // then
        assertThat(line).contains(",\"'=HYPERLINK(\"\"x\"\"),1\",");
    }

    private IncidentAlertRow row(IncidentResponseValue response, Long respondedAtMs, Integer hrBpm, String reason,
                                 Boolean onBody, Long deliveredOffsetMs) {
        boolean hasDecision = hrBpm != null;
        Boolean delivered = null;
        Long deliveredAtMs = null;
        if (deliveredOffsetMs != null) {
            delivered = true;
            deliveredAtMs = OPENED_AT_MS + deliveredOffsetMs;
        }
        Long measuredAtMs = null;
        String accuracy = null;
        String wearState = null;
        if (hasDecision) {
            measuredAtMs = OPENED_AT_MS;
            accuracy = "HIGH";
        }
        if (onBody != null) {
            wearState = "WORN_VALID";
        }
        ResponseVia via = ResponseVia.WATCH;
        return new IncidentAlertRow(OPENED_AT_MS, "inc-1", 28L, IncidentKind.HR_ANOMALY, "run-01", 1, "dec-1",
                "EMERGENCY", hrBpm, measuredAtMs, accuracy, reason, onBody, wearState, RESPOND_BY_MS, response,
                respondedAtMs, via, IncidentState.OK_CLOSED, OPENED_AT_MS, delivered, deliveredAtMs,
                IncidentOutcome.FALSE_ALARM);
    }
}
