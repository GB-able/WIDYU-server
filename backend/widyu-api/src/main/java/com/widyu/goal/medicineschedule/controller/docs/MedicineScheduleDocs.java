package com.widyu.goal.medicineschedule.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.medicineschedule.dto.request.CreateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.request.UpdateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.response.MedicineHomeResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineMonthlyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDetailResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleIdResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleTodayResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Medicine Schedule", description = "약 복용 관리 API")
public interface MedicineScheduleDocs {

    @Operation(
            summary = "당일 약 복용 현황 조회",
            description = """
                    오늘 날짜의 약 복용 스케줄과 현황을 조회합니다.

                    **기능:**
                    - 오늘 복용해야 할 모든 약 스케줄 조회
                    - 각 스케줄별 알람 시간과 약품 목록 반환

                    **권한:**
                    - memberId가 null → 본인의 약 복용 현황 조회
                    - memberId가 있음 → 보호자가 가족으로 연결된 시니어의 약 복용 현황 조회
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineScheduleTodayResponse> getTodaySchedules(
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 복용 홈 - 날짜별 약 조회",
            description = """
                    지정한 년월의 약 복용 통계를 조회합니다.

                    **기능:**
                    - 이전 달 / 현재 달(또는 요청 달) 목표 달성 통계
                    - 일별 목표 달성률 배열 반환

                    **권한:**
                    - memberId가 null → 본인의 약 복용 통계 조회
                    - memberId가 있음 → 보호자가 가족으로 연결된 시니어의 약 복용 통계 조회
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineMonthlyResponse> getMonthlyStats(
            @Parameter(description = "연도", example = "2024", required = true)
            @RequestParam int year,
            @Parameter(description = "월", example = "11", required = true)
            @RequestParam int month,
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "시니어 약복용 홈 조회",
            description = """
                    시니어의 모든 약 복용 스케줄을 조회합니다.

                    **기능:**
                    - 등록된 모든 약 복용 스케줄 조회
                    - 각 스케줄별 알람 시간과 약품 상세 정보 반환

                    **권한:**
                    - memberId가 null → 본인의 약 복용 스케줄 조회
                    - memberId가 있음 → 보호자가 가족으로 연결된 시니어의 약 복용 스케줄 조회
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineHomeResponse> getHomeSchedules(
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 복용 상세 조회",
            description = """
                    특정 약 복용 스케줄의 상세 정보를 조회합니다.

                    **기능:**
                    - 스케줄의 알람 시간, 카테고리, 약품 상세 정보 조회

                    **권한:**
                    - 본인 또는 보호자만 조회 가능
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 스케줄"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineScheduleDetailResponse> getScheduleDetail(
            @Parameter(description = "약 복용 스케줄 ID", required = true)
            @PathVariable Long scheduleId,
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "시니어 약 복용 생성 / 보호자가 시니어 약 복용 대신 생성",
            description = """
                    새로운 약 복용 스케줄을 생성합니다.

                    **기능:**
                    - 알람 시간, 카테고리, 약품 정보를 포함한 스케줄 생성
                    - 약품이 DB에 없으면 자동으로 생성

                    **권한:**
                    - memberId가 null → 본인의 약 복용 스케줄 생성 (시니어)
                    - memberId가 있음 → 보호자가 가족으로 연결된 시니어의 약 복용 스케줄 생성
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineScheduleIdResponse> createSchedule(
            @Valid @RequestBody CreateMedicineScheduleRequest request,
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 수정",
            description = """
                    기존 약 복용 스케줄을 수정합니다.

                    **기능:**
                    - 알람 시간, 카테고리, 약품 정보 수정

                    **권한:**
                    - 본인 또는 보호자만 수정 가능
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 스케줄"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<Void> updateSchedule(
            @Parameter(description = "약 복용 스케줄 ID", required = true)
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateMedicineScheduleRequest request,
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 스케줄 삭제하기",
            description = """
                    약 복용 스케줄을 삭제합니다.

                    **기능:**
                    - 스케줄 상태를 DELETED로 변경 (soft delete)

                    **권한:**
                    - 본인 또는 보호자만 삭제 가능
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 스케줄"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<Void> deleteSchedule(
            @Parameter(description = "약 복용 스케줄 ID", required = true)
            @PathVariable Long scheduleId,
            @Parameter(description = "대상 시니어 ID (null이면 본인)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 복용 인증하기",
            description = """
                    약 복용을 인증합니다.

                    **기능:**
                    - 약 복용 인증 이미지 업로드
                    - 알람 시간 전후 30분 이내에만 인증 가능
                    - 당일 중복 인증 불가

                    **권한:**
                    - 시니어 본인만 가능
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "시간 범위 초과 또는 이미 인증 완료"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<Void> verifyMedication(
            @Parameter(description = "약 복용 스케줄 ID", required = true)
            @PathVariable Long scheduleId,
            @Parameter(description = "약 복용 인증 이미지")
            @RequestPart(required = false) List<MultipartFile> medicationProofImage
    );

    @Operation(
            summary = "약품 검색하기",
            description = """
                    약품명으로 약품을 검색합니다.

                    **기능:**
                    - 약품명 키워드로 검색
                    - 약품 정보 (이름, 이미지, 설명, 용법, 효능) 반환

                    **특이사항:**
                    - 추후 공공 데이터 API 연동 예정
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponseTemplate<MedicineSearchResponse> searchMedicines(
            @Parameter(description = "검색 키워드", required = true)
            @RequestParam String keyword
    );
}
