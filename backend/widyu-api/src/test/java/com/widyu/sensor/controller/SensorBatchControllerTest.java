package com.widyu.sensor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;

import com.widyu.global.util.SecurityUtil;
import com.widyu.sensor.SensorIngestChannel;
import com.widyu.sensor.application.SensorBatchService;
import com.widyu.sensor.dto.response.SensorBatchResult;
import com.widyu.sensor.dto.response.SensorBatchResultResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SensorBatchController 단위 테스트")
class SensorBatchControllerTest {

    @Mock private SensorBatchService sensorBatchService;
    @Mock private SecurityUtil securityUtil;
    @InjectMocks private SensorBatchController controller;

    @Test
    @DisplayName("REST로 묶음을 받으면 경로를 REST로 넘겨 저장 결과를 응답한다")
    void REST로_묶음을_받으면_경로를_REST로_넘긴다() {
        // given
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        SensorBatchResultResponse stored =
                SensorBatchResultResponse.of("01j8zk3v9x2q4m7n8p1r5s6t7u", 1L, SensorBatchResult.STORED);
        given(securityUtil.getCurrentMemberId()).willReturn(42L);
        given(sensorBatchService.ingest(eq(42L), same(payload), eq(SensorIngestChannel.REST))).willReturn(stored);

        // when
        SensorBatchResultResponse response = controller.ingestBatch(payload).getData();

        // then
        assertThat(response).isSameAs(stored);
    }
}
