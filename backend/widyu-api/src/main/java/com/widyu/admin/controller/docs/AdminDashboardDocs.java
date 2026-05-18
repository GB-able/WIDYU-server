package com.widyu.admin.controller.docs;

import com.widyu.admin.dto.response.AdminDashboardResponse;
import com.widyu.global.response.ApiResponseTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
public interface AdminDashboardDocs {

    @Operation(summary = "대시보드 통계 조회", description = "회원·앨범·결제·심박수 응급 통계를 반환합니다.")
    ApiResponseTemplate<AdminDashboardResponse> getDashboard();
}
