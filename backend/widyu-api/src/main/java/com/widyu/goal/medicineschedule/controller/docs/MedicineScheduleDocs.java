package com.widyu.goal.medicineschedule.controller.docs;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.goal.medicineschedule.dto.request.CreateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.request.UpdateMedicineScheduleRequest;
import com.widyu.goal.medicineschedule.dto.response.MedicationProofResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicationAlarmSyncResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineHomeResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineMonthlyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDetailResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleChangeResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleIdResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Medicine Schedule", description = "약 복용 관리 API")
public interface MedicineScheduleDocs {

    @Operation(
            summary = "복약 알람 스냅샷 조회",
            description = """
                    앱이 OS 로컬 알람을 동기화하기 위한 본인의 복약 스케줄과 인증 완료 정보를 조회합니다.

                    `schedules`는 오늘 이후 하루라도 유효한 스케줄 버전입니다. 오늘까지만 유효한 버전(오늘 인증이 끝난 뒤
                    지우거나 시각을 바꾼 알람)과 내일부터 시작하는 버전이 함께 들어갑니다.
                    앱은 날짜마다 `effectiveFrom ≤ 날짜 ≤ effectiveTo`인 스케줄만 로컬 알람으로 거세요.
                    `effectiveTo`가 null이면 끝나는 날이 없습니다. 날짜는 `timeZone` 기준입니다.

                    `revision`은 복약 스케줄 또는 오늘 복용 인증이 바뀔 때 증가합니다.
                    FCM data에 `revision`이 있고 앱이 가진 값과 다르거나, 앱이 포그라운드가 될 때 이 API를 호출하세요.
                    `revision`은 복약 일정 등록·변경·삭제·동기화(`notificationType=MEDICATION_SCHEDULE_CREATED|CHANGED|DELETED|SYNC`),
                    복약 리마인더, 정시 복약 푸시(`notificationType=MEDICATION_ON_TIME`)에 실립니다.
                    `completed`의 `scheduleId:yyyy-MM-dd` 항목은 해당 일자의 로컬 알람을 건너뜁니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicationAlarmSyncResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2010",
                                              "message": "복약 알람 동기화 조회 성공",
                                              "data": {
                                                "revision": 42,
                                                "timeZone": "Asia/Seoul",
                                                "schedules": [
                                                  {"scheduleId": 15, "alarmTime": "08:00", "doseCount": 1.0, "effectiveFrom": "2026-10-01", "effectiveTo": "2026-11-09"},
                                                  {"scheduleId": 17, "alarmTime": "19:27", "doseCount": 2.0, "effectiveFrom": "2026-10-15", "effectiveTo": null},
                                                  {"scheduleId": 21, "alarmTime": "20:00", "doseCount": 2.0, "effectiveFrom": "2026-11-10", "effectiveTo": null}
                                                ],
                                                "completed": ["15:2026-11-09"]
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponseTemplate<MedicationAlarmSyncResponse> getAlarmSync();

    @Operation(
            summary = "일자별 약 복용 현황 조회",
            description = """
                    선택한 날짜의 약 복용 스케줄과 복용 인증 현황을 조회합니다.

                    **기능:**
                    - 활성 약 스케줄 목록 조회 (알람 시간·약품 목록)
                    - 각 스케줄의 선택 날짜 기준 복용 상태(status) 반환
                      - DONE: 복용 인증 완료
                      - UPCOMING: 아직 인증 마감(알람+30분) 전 (미복용)
                      - MISSED: 인증 마감이 지났는데 미인증
                    - 각 스케줄의 복용 인증 이미지(proofImageUrl) 반환 (인증 없으면 null)

                    **파라미터:**
                    - date: 조회할 날짜 (yyyy-MM-dd)

                    **권한:**
                    - memberId가 null → 본인의 약 복용 현황 조회
                    - memberId가 있음 → 보호자가 가족으로 연결된 시니어의 약 복용 현황 조회
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineScheduleDailyResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2001",
                                              "message": "일자별 약 복용 현황 조회 성공",
                                              "data": {
                                                "medicineSchedules": [
                                                  {
                                                    "medicineScheduleId": 1,
                                                    "totalCount": 3.0,
                                                    "alarmTime": "08:00",
                                                    "status": "DONE",
                                                    "proofImageUrl": "https://www.widyu.shop/proof/1.jpg",
                                                    "medicines": [
                                                      {
                                                        "name": "타이레놀",
                                                        "count": 1.0
                                                      },
                                                      {
                                                        "name": "비타민C",
                                                        "count": 2.0
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "medicineScheduleId": 2,
                                                    "totalCount": 1.0,
                                                    "alarmTime": "20:00",
                                                    "status": "UPCOMING",
                                                    "proofImageUrl": null,
                                                    "medicines": [
                                                      {
                                                        "name": "오메가3",
                                                        "count": 1.0
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineScheduleDailyResponse> getDailySchedules(
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd)", example = "2026-07-06")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
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

                    **상단 두 달 비교(comparison):**
                    - right는 항상 이번 달, left는 요청 달이 이번 달이면 지난달이고 아니면 요청 달
                    - achieved는 복용한 횟수, total은 예정 복용 횟수(알람 1개 = 1회), rate는 achieved ÷ total 정수 퍼센트(반올림)
                    - 이번 달은 1일~오늘, 지난 달은 1일~말일을 셈. 이번 달 이후 달은 total 0, rate null
                    - "15/30회"는 achieved/total, "67%"는 rate로 표시
                    - lastMonthAchieveCount, currentMonthAchieveCount(모두 먹은 날 수)는 폐기 예정

                    **권한:**
                    - memberId가 null → 본인의 약 복용 통계 조회
                    - memberId가 있음 → 보호자가 가족으로 연결된 시니어의 약 복용 통계 조회

                    **조회 가능한 달 (한국 시간 기준):**
                    - 이번 달 기준 지난 12개월부터 앞으로 12개월까지, 벗어나면 400 GOAL_4000
                    - 보호자는 이번 달 이후를 조회할 수 없음, 요청하면 400 GOAL_4001
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineMonthlyResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2002",
                                              "message": "날짜별 약 조회 성공",
                                              "data": {
                                                "lastMonthAchieveCount": 25,
                                                "currentMonthAchieveCount": 18,
                                                "monthlyGoalRates": [
                                                  1.0, 1.0, 0.5, 1.0, 1.0, 0.0, 1.0,
                                                  1.0, 1.0, 1.0, 0.5, 1.0, 1.0, 1.0,
                                                  1.0, 0.5, 1.0, 1.0, 0.0, 0.0, 0.0,
                                                  0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                                                  0.0, 0.0
                                                ],
                                                "comparison": {
                                                  "left": {"year": 2026, "month": 10, "achieved": 20, "total": 31, "rate": 65},
                                                  "right": {"year": 2026, "month": 11, "achieved": 5, "total": 11, "rate": 45}
                                                }
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "조회할 수 없는 달 (GOAL_4000, 보호자의 미래 달 GOAL_4001)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineMonthlyResponse> getMonthlyStats(
            @Parameter(description = "연도", example = "2024", required = true)
            @RequestParam
            @Min(value = 2000, message = "year는 2000 이상 2100 이하여야 합니다.")
            @Max(value = 2100, message = "year는 2000 이상 2100 이하여야 합니다.")
            int year,
            @Parameter(description = "월", example = "11", required = true)
            @RequestParam
            @Min(value = 1, message = "month는 1 이상 12 이하여야 합니다.")
            @Max(value = 12, message = "month는 1 이상 12 이하여야 합니다.")
            int month,
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
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineHomeResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2003",
                                              "message": "시니어 약복용 홈 조회 성공",
                                              "data": {
                                                "medicineSchedules": [
                                                  {
                                                    "medicineScheduleId": 1,
                                                    "totalCount": 3.0,
                                                    "alarmTime": "08:00",
                                                    "medicines": [
                                                      {
                                                        "name": "타이레놀",
                                                        "count": 1.0
                                                      },
                                                      {
                                                        "name": "비타민C",
                                                        "count": 2.0
                                                      }
                                                    ]
                                                  },
                                                  {
                                                    "medicineScheduleId": 2,
                                                    "totalCount": 1.0,
                                                    "alarmTime": "20:00",
                                                    "medicines": [
                                                      {
                                                        "name": "오메가3",
                                                        "count": 1.0
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            ),
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
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineScheduleDetailResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2004",
                                              "message": "약 복용 상세 조회 성공",
                                              "data": {
                                                "alarmTime": "08:00",
                                                "totalCount": 3.0,
                                                "categories": [
                                                  {
                                                    "categoryId": 1,
                                                    "name": "아침 식후",
                                                    "countSum": 3.0,
                                                    "medicines": [
                                                      {
                                                        "medicineId": 1,
                                                        "medicineName": "타이레놀",
                                                        "dose": 1.0,
                                                        "imageUrl": "https://example.com/tylenol.jpg",
                                                        "description": "해열, 진통제"
                                                      },
                                                      {
                                                        "medicineId": 2,
                                                        "medicineName": "비타민C",
                                                        "dose": 2.0,
                                                        "imageUrl": "https://example.com/vitaminc.jpg",
                                                        "description": "면역력 강화"
                                                      }
                                                    ]
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            ),
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
            summary = "시니어 복약 일정 생성",
            description = """
                    새로운 약 복용 스케줄을 생성합니다.

                    **기능:**
                    - 알람 시간, 카테고리, 약품 정보를 포함한 스케줄 생성
                    - 오늘 인증 가능 시간(알람 ±30분)이 남은 시각이면 오늘부터, 이미 지난 시각이면 다음 날부터 적용합니다.
                    - 응답의 scheduleRevision은 시니어의 복약 알람 revision입니다.
                    - 시니어 한 명당 약 알람은 최대 6개입니다. 종료되지 않은 알람(내일부터 시작하는 알람 포함)이 이미 6개면 400입니다. 삭제하면 바로 자리가 비고, 수정은 개수를 바꾸지 않습니다.
                    - 같은 시각의 종료되지 않은 알람(내일부터 시작하는 알람 포함)이 이미 있으면 409(`MEDICINE_SCHEDULE_4090`)입니다. 지웠거나 시각을 바꾼 옛 알람과 같은 시각은 등록할 수 있습니다.

                    **권한:**
                    - 현재 가족 방장만 등록할 수 있습니다. 대상 시니어 memberId는 필수입니다.
                    - 비방장 보호자와 시니어 본인은 403입니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateMedicineScheduleRequest.class),
                            examples = @ExampleObject(
                                    name = "약 복용 스케줄 생성 예시",
                                    value = """
                                            {
                                              "alarmTime": "08:00",
                                              "categories": [
                                                {
                                                  "name": "위염약",
                                                  "medicines": [
                                                    {
                                                      "itemName": "이노엔비타메진캡슐",
                                                      "dose": 1.5,
                                                      "itemImage": "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/1OSm8xoWGbV",
                                                      "usage": "성인은 1회 2캡슐씩, 1일 1~2회 복용합니다.\\n",
                                                      "efficacy": "이 약은 육체피로, 임신ㆍ수유기, 병중ㆍ병후(병을 앓는 동안이나 회복 후)의 체력 저하 시 비타민 B1, B6의 보급과 신경통, 근육통, 관절통(요통, 어깨결림 등), 각기, 눈의 피로, 구각염(입꼬리염), 구순염(입술염), 구내염(입안염), 설염(혀염), 습진, 피부염 증상의 완화에 사용합니다.\\n"
                                                    }
                                                  ]
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineScheduleIdResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2005",
                                              "message": "약 복용 스케줄 생성 성공",
                                              "data": {
                                                "medicineScheduleId": 1,
                                                "scheduleRevision": 7,
                                                "effectiveFromDate": "2026-10-02"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "같은 시각의 약 알람이 이미 있음 (MEDICINE_SCHEDULE_4090)")
    })
    ApiResponseTemplate<MedicineScheduleIdResponse> createSchedule(
            @Valid @RequestBody CreateMedicineScheduleRequest request,
            @Parameter(description = "대상 시니어 ID (방장 쓰기에서 필수)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 수정",
            description = """
                    기존 약 복용 스케줄을 수정합니다.

                    **기능:**
                    - 알람 시간, 카테고리, 약품 정보 수정
                    - 오늘 아직 끝나지 않은 알람(미인증이고 인증 가능 시간이 남음)은 오늘부터 바뀝니다.
                    - 오늘 끝난 알람(인증 완료, 또는 인증 가능 시간이 지난 미인증)은 오늘 기록을 그대로 두고 다음 날부터 바뀝니다.
                    - 적용일 전날까지의 기록은 기존 버전에 남습니다. 적용일 전날에 유효하지 않았던 버전(오늘 시작·시작 전)은 해당 버전을 그대로 갱신합니다.
                    - 응답의 medicineScheduleId는 적용될 버전 ID, effectiveFromDate는 적용 시작일입니다.
                    - 이미 종료된 과거 버전(닫힌 scheduleId)은 수정할 수 없습니다.
                    - 바꿀 시각이 다른 종료되지 않은 알람(내일부터 시작하는 알람 포함)과 같으면 409(`MEDICINE_SCHEDULE_4090`)입니다. 자기 시각을 그대로 두고 약만 바꾸는 수정은 됩니다.

                    **권한:**
                    - 현재 가족 방장만 수정할 수 있습니다. 대상 시니어 memberId는 필수입니다.
                    - 비방장 보호자와 시니어 본인은 403입니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateMedicineScheduleRequest.class),
                            examples = @ExampleObject(
                                    name = "약 복용 스케줄 수정 예시",
                                    value = """
                                            {
                                              "alarmTime": "09:00",
                                              "categories": [
                                                {
                                                  "name": "위염약",
                                                  "medicines": [
                                                    {
                                                      "itemName": "이노엔비타메진캡슐",
                                                      "dose": 2.5,
                                                      "itemImage": "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/1OSm8xoWGbV",
                                                      "usage": "성인은 1회 2캡슐씩, 1일 1~2회 복용합니다.\\n",
                                                      "efficacy": "이 약은 육체피로, 임신ㆍ수유기, 병중ㆍ병후(병을 앓는 동안이나 회복 후)의 체력 저하 시 비타민 B1, B6의 보급과 신경통, 근육통, 관절통(요통, 어깨결림 등), 각기, 눈의 피로, 구각염(입꼬리염), 구순염(입술염), 구내염(입안염), 설염(혀염), 습진, 피부염 증상의 완화에 사용합니다.\\n"
                                                    }
                                                  ]
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineScheduleIdResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"MEDICINE_2006","message":"약 복용 스케줄 수정 성공",
                                     "data":{"medicineScheduleId":2,"scheduleRevision":8,"effectiveFromDate":"2026-10-02"}}
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 스케줄"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "같은 시각의 약 알람이 이미 있음 (MEDICINE_SCHEDULE_4090)")
    })
    ApiResponseTemplate<MedicineScheduleIdResponse> updateSchedule(
            @Parameter(description = "약 복용 스케줄 ID", required = true)
            @PathVariable Long scheduleId,
            @Valid @RequestBody UpdateMedicineScheduleRequest request,
            @Parameter(description = "대상 시니어 ID (방장 쓰기에서 필수)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 스케줄 삭제하기",
            description = """
                    약 복용 스케줄을 삭제합니다.

                    **기능:**
                    - 오늘 아직 끝나지 않은 알람은 오늘부터 중단합니다 (적용 종료일을 어제로 마감).
                    - 오늘 끝난 알람(인증 완료, 또는 인증 가능 시간이 지난 미인증)은 오늘 기록을 그대로 두고 다음 날부터 중단합니다.
                    - 응답에 scheduleRevision과 effectiveFromDate(중단이 시작되는 날)를 반환합니다.

                    **권한:**
                    - 현재 가족 방장만 삭제할 수 있습니다. 대상 시니어 memberId는 필수입니다.
                    - 비방장 보호자와 시니어 본인은 403입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineScheduleChangeResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"MEDICINE_2007","message":"약 복용 스케줄 삭제 성공",
                                     "data":{"scheduleRevision":9,"effectiveFromDate":"2026-10-02"}}
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 스케줄"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ApiResponseTemplate<MedicineScheduleChangeResponse> deleteSchedule(
            @Parameter(description = "약 복용 스케줄 ID", required = true)
            @PathVariable Long scheduleId,
            @Parameter(description = "대상 시니어 ID (방장 쓰기에서 필수)", example = "1")
            @RequestParam(required = false) Long memberId
    );

    @Operation(
            summary = "약 복용 인증하기",
            description = """
                    약 복용을 인증합니다.

                    **기능:**
                    - 약 복용 인증 이미지 업로드 (형식은 파일 내용으로 판별, 앨범 사진과 같은 형식, 장당 10MB까지. 한 장이라도 어긋나면 아무것도 올리지 않음)
                    - 알람 시간 전후 30분 이내에만 인증 가능
                    - 당일 중복 인증 불가

                    **응답:**
                    - `currentPoints`: 이번 인증분까지 반영된 보유 포인트입니다.
                      (시니어 프로필이 없는 회원은 적립 없이 0)
                    - `earnedPoints`: 이번 인증으로 적립된 포인트입니다. 기본 10p이며,
                      이번 인증으로 그날 유효한 복용 일정을 모두 채우면 보너스 20p를 더해 30p입니다.
                      (시니어 프로필이 없는 회원은 계산값만 내려가고 실제 적립은 되지 않습니다)
                    - 포인트는 인증 성공과 같은 트랜잭션에서 즉시 적립됩니다.
                      포인트 잔액 동시 변경으로 충돌하면 인증 전체가 롤백되고 409를 응답하니 재시도하세요.

                    **권한:**
                    - 시니어 본인만 가능
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "시간 범위 초과 또는 이미 인증 완료, FILE_4002: JPEG, PNG, GIF, WEBP, BMP, HEIC, HEIF가 아닌 파일(SVG 포함), FILE_4004: 10MB 초과"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "포인트 잔액 동시 변경 충돌")
    })
    ApiResponseTemplate<MedicationProofResponse> verifyMedication(
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
                    - 약품 정보 (이름, 이미지, 용법, 효능) 반환

                    **특이사항:**
                    - 공공 데이터 API 연동되어 있음
                    - 앞뒤 공백을 뺀 키워드가 2글자 미만이면 검색하지 않고 빈 목록(`medicines: []`)을 반환합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MedicineSearchResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "code": "MEDICINE_2009",
                                              "message": "약품 검색 성공",
                                              "data": {
                                                "medicines": [
                                                  {
                                                    "medicineId": 1,
                                                    "itemName": "타이레놀",
                                                    "itemImage": "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/154603080625500106",
                                                    "usage": "1일 3회, 식후 30분에 복용",
                                                    "efficacy": "두통, 치통, 발치 후 동통, 인후통, 이통, 관절통, 신경통, 요통, 근육통, 생리통, 타박통, 염좌통의 진통과 오한, 발열 시의 해열"
                                                  },
                                                  {
                                                    "medicineId": 2,
                                                    "itemName": "타이레놀 이알서방정",
                                                    "itemImage": "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/147427291350900121",
                                                    "usage": "성인 및 12세 이상의 소아는 1회 2정을 1일 3회 복용",
                                                    "efficacy": "두통, 신경통, 생리통, 치통의 진통 및 해열"
                                                  }
                                                ]
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponseTemplate<MedicineSearchResponse> searchMedicines(
            @Parameter(description = "검색 키워드 (2글자 이상)", required = true, example = "타이레놀")
            @RequestParam String keyword
    );
}
