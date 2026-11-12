package com.widyu.location.access.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocationAccessLogCsv 위치 열람 기록 CSV 행 규칙 테스트")
class LocationAccessLogCsvTest {

    private static final LocalDateTime ACCESSED_AT = LocalDateTime.of(2026, 9, 21, 10, 12, 3);

    @Test
    @DisplayName("수식 문자로 시작하는 보호자 이름은 작은따옴표를 붙여 수식으로 실행되지 않게 한다")
    void 수식_문자로_시작하는_보호자_이름은_작은따옴표를_붙인다() {
        // given
        LocationAccessLog accessLog = LocationAccessLog.of(55L, 28L, LocationAccessPath.REST_TRAIL, ACCESSED_AT);

        // when
        String row = LocationAccessLogCsv.row(accessLog, "=HYPERLINK(\"x\")");

        // then
        assertThat(row).isEqualTo("2026-09-21 10:12:03,1789953123000,55,\"'=HYPERLINK(\"\"x\"\")\",REST_TRAIL,"
                + "최근 15분 이동 경로 조회,아니오,\n");
    }

    @Test
    @DisplayName("쉼표가 든 보호자 이름은 큰따옴표로 감싸고 숫자 칸은 그대로 둔다")
    void 쉼표가_든_보호자_이름은_큰따옴표로_감싼다() {
        // given
        LocationAccessLog accessLog = LocationAccessLog.of(55L, 28L, LocationAccessPath.HOME_OUTING, ACCESSED_AT);

        // when
        String row = LocationAccessLogCsv.row(accessLog, "김,보호");

        // then
        assertThat(row).startsWith("2026-09-21 10:12:03,1789953123000,55,\"김,보호\",HOME_OUTING,홈 화면 외출 여부 확인,");
    }

    @Test
    @DisplayName("모든 경로 코드에 사람이 읽는 설명이 있다")
    void 모든_경로_코드에_사람이_읽는_설명이_있다() {
        // when & then
        for (LocationAccessPath path : LocationAccessPath.values()) {
            assertThat(LocationAccessLogCsv.pathLabel(path)).isNotBlank();
        }
    }
}
