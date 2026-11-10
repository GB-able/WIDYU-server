package com.widyu.incident.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Alert Record Export", description = "알림 한 건마다 판정 근거와 어르신 응답 CSV 내보내기 API (운영자)")
public interface AdminAlertRecordExportDocs {

    @Operation(
            summary = "알림 기록 CSV 내보내기",
            description = """
                    기간 안에 열린 알림(서버가 심박·낙상 이상을 감지해 연 사건) 한 건마다 한 줄인 CSV를 내려받습니다.
                    회차가 없어도 됩니다. 시각은 **한국 시간**으로 받고, 알림 시각이 `[from, to)`인 행을 싣습니다(최대 31일).
                    어르신이 직접 누른 도움 요청은 싣지 않습니다. 이름·전화번호는 없고 회원 ID만 있습니다.

                    칸:
                    - 알림: `alert_at_kst`, `alert_at_ms`, `incident_id`, `member_id`, `kind`(`HR_ANOMALY`/`FALL_SUSPECTED`),
                      `run_id`, `detection_count`(같은 상황에 붙은 감지 수), `decision_id`, `severity`
                    - 판정 근거: `hr_bpm`, `hr_measured_at_ms`, `hr_accuracy`, `ai_reason`, `on_body`, `wear_state`.
                      **회차가 있는 알림만** 채워지고 회차 없는 알림은 빈칸입니다
                    - `baseline_bpm`(본인 기준값), `held_seconds`(이어진 시간), `activity_state`(활동 상태):
                      **아직 서버에 남지 않아 항상 빈칸**입니다(ADR-0042 이후 채움)
                    - 어르신 응답: `respond_by_ms`(응답 마감), `senior_response`, `response`(실제 보낸 값), `responded_at_ms`,
                      `response_via`, `state`
                      - `senior_response`: `OK`(마감 전 괜찮아요), `HELP`(도움 요청), `NO_RESPONSE`(마감까지 응답 없음.
                        마감과 같거나 늦은 괜찮아요 포함, 그때 `response`는 `OK`), `PENDING`(아직 마감 전)
                    - 보호자: `guardian_alert_queued_at_ms`(보호자 최초 알림 단계에 들어간 시각. 보호자가 없어도 찍힘),
                      `guardian_push_delivered`·`guardian_push_delivered_at_ms`(FCM 첫 전송 성공, 회차 있는 알림만), `outcome`(보호자 사후 판정)

                    관리자 접속기록에 관리자와 요청 경로가 남습니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "CSV (`text/csv`, UTF-8)")
    @ApiResponse(
            responseCode = "400",
            description = "기간 오류(`from >= to`, 31일 초과) 또는 날짜 형식 오류",
            content = @Content(examples = @ExampleObject(value = """
                    {"code": "SENSOR_4004", "message": "내보내기 시간대가 올바르지 않습니다.", "data": null}
                    """))
    )
    ResponseEntity<byte[]> export(
            @Parameter(description = "시작 시각(포함), 한국 시간", example = "2026-11-01T00:00:00") LocalDateTime from,
            @Parameter(description = "끝 시각(제외), 한국 시간", example = "2026-11-08T00:00:00") LocalDateTime to);
}
