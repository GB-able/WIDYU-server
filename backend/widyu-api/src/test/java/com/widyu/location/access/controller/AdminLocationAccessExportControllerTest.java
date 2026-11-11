package com.widyu.location.access.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.widyu.global.error.GlobalExceptionHandler;
import com.widyu.location.access.application.LocationAccessExportService;
import com.widyu.location.access.dto.response.LocationAccessExportFile;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLocationAccessExportController 응답 형식 테스트")
class AdminLocationAccessExportControllerTest {

    @Mock private LocationAccessExportService locationAccessExportService;
    @InjectMocks private AdminLocationAccessExportController controller;

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("한국 시간 기간으로 내보내면 CSV 첨부 파일로 내려준다")
    void 한국_시간_기간으로_내보내면_CSV_첨부_파일로_내려준다() throws Exception {
        // given
        byte[] csv = "﻿머리글\n".getBytes(UTF_8);
        given(locationAccessExportService.export(
                28L, LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0)))
                .willReturn(LocationAccessExportFile.of("location_access_member28.csv", csv));

        // when
        MvcResult result = mockMvc().perform(get("/api/v1/admin/members/28/location-access-exports")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-11-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"location_access_member28.csv\""))
                .andReturn();

        // then
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(csv);
    }

    @Test
    @DisplayName("끝 시각 없이 내보내면 400을 반환한다")
    void 끝_시각_없이_내보내면_400을_반환한다() throws Exception {
        // when & then
        mockMvc().perform(get("/api/v1/admin/members/28/location-access-exports")
                        .param("from", "2026-08-01T00:00:00"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(locationAccessExportService);
    }
}
