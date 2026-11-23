package com.widyu.location.access.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("LocationAccessLogRepository 미통보 요약 대상 테스트")
class LocationAccessLogRepositoryTest {

    private static final Long VIEWER_ID = 55L;
    private static final Long HOME_ONLY_SENIOR_ID = 7L;
    private static final Long MAP_SENIOR_ID = 8L;

    @Autowired private LocationAccessLogRepository locationAccessLogRepository;

    @Test
    @DisplayName("홈 외출 여부 열람만 미통보로 남은 시니어는 요약 대상에서 빠진다")
    void 홈_외출_여부_열람만_있는_시니어는_요약_대상이_아니다() {
        // given
        LocalDateTime accessedAt = LocalDateTime.of(2026, 11, 23, 9, 0);
        locationAccessLogRepository.saveAndFlush(
                LocationAccessLog.of(VIEWER_ID, HOME_ONLY_SENIOR_ID, LocationAccessPath.HOME_OUTING, accessedAt));
        locationAccessLogRepository.saveAndFlush(
                LocationAccessLog.of(VIEWER_ID, MAP_SENIOR_ID, LocationAccessPath.REST_LAST, accessedAt));

        // when & then
        assertThat(locationAccessLogRepository.findSeniorIdsWithUnnotified())
                .containsExactly(MAP_SENIOR_ID);
    }

    @Test
    @DisplayName("미통보 행을 선점하면 홈 외출 여부 행은 남기고 지도 열람 행만 통보 시각을 채운다")
    void 선점은_홈_외출_여부_행을_건드리지_않는다() {
        // given
        LocalDateTime accessedAt = LocalDateTime.of(2026, 11, 23, 9, 0);
        LocationAccessLog home = locationAccessLogRepository.saveAndFlush(
                LocationAccessLog.of(VIEWER_ID, MAP_SENIOR_ID, LocationAccessPath.HOME_OUTING, accessedAt));
        LocationAccessLog map = locationAccessLogRepository.saveAndFlush(
                LocationAccessLog.of(VIEWER_ID, MAP_SENIOR_ID, LocationAccessPath.REST_LAST, accessedAt));
        LocalDateTime now = accessedAt.plusMinutes(1);

        // when
        int claimed = locationAccessLogRepository.claimUnnotified(MAP_SENIOR_ID, now);

        // then
        assertThat(claimed).isEqualTo(1);
        assertThat(locationAccessLogRepository.findById(home.getId()).orElseThrow().getNotifiedAt()).isNull();
        assertThat(locationAccessLogRepository.findById(map.getId()).orElseThrow().getNotifiedAt()).isEqualTo(now);
    }
}
