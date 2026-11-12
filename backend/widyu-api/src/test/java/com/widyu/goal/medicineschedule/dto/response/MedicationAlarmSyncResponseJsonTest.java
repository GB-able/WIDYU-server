package com.widyu.goal.medicineschedule.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

@JsonTest
@DisplayName("MedicationAlarmSyncResponse JSON 계약 테스트")
class MedicationAlarmSyncResponseJsonTest {

    @Autowired private JacksonTester<MedicationAlarmSyncResponse> json;

    @Test
    @DisplayName("응답을 직렬화하면 유효 기간을 yyyy-MM-dd 문자열로 싣고 끝나는 날이 없으면 null로 싣는다")
    void 응답을_직렬화하면_유효_기간을_날짜_문자열로_싣고_끝나는_날이_없으면_null로_싣는다() throws Exception {
        // given: 앱은 이 형식으로 날짜마다 알람을 걸지 정한다(LLD-0125)
        MedicationAlarmSyncResponse response = new MedicationAlarmSyncResponse(45L, "Asia/Seoul", List.of(
                new MedicationAlarmSyncResponse.Schedule(15L, "08:00", 1.0,
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 9)),
                new MedicationAlarmSyncResponse.Schedule(21L, "20:00", 2.5, LocalDate.of(2026, 11, 10), null)),
                List.of("15:2026-11-09"));

        // when
        JsonContent<MedicationAlarmSyncResponse> content = json.write(response);

        // then
        assertThat(content).extractingJsonPathStringValue("$.schedules[0].effectiveFrom").isEqualTo("2026-10-01");
        assertThat(content).extractingJsonPathStringValue("$.schedules[0].effectiveTo").isEqualTo("2026-11-09");
        assertThat(content).extractingJsonPathStringValue("$.schedules[1].effectiveFrom").isEqualTo("2026-11-10");
        assertThat(content.getJson()).contains("\"effectiveTo\":null");
        // 반 알약 복용량은 소수로, 정수 복용량도 1.0으로 싣는다 (#1001)
        assertThat(content.getJson()).contains("\"doseCount\":1.0").contains("\"doseCount\":2.5");
    }
}
