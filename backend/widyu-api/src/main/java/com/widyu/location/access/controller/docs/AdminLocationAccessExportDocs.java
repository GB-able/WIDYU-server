package com.widyu.location.access.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;

@Tag(name = "Admin Location Access Export", description = "어르신 위치 열람 기록 CSV 내보내기 API (운영자)")
public interface AdminLocationAccessExportDocs {

    @Operation(
            summary = "어르신 위치 열람 기록 CSV 내보내기",
            description = """
                    어르신 한 분의 위치를 보호자가 언제·누가·어떤 경로로 봤는지를 기간으로 묶어 CSV 파일로 내려받습니다.
                    어르신이 고객센터에 요청하면 본인 확인 뒤 이메일로 보내 드리는 데 씁니다(위치정보 이용약관 제6조).

                    - 기간: `from`·`to`는 **한국 시간**, 양 끝 포함, 최대 1년
                    - 행: 열람 시각 오름차순. 기록이 없으면 머리글만 있습니다
                    - 파일: UTF-8(BOM 포함, 엑셀에서 한글이 깨지지 않습니다)

                    칸:
                    - `열람 시각(한국 시간)`, `열람 시각(epoch ms)`
                    - `보호자 회원 ID`, `보호자 이름`: 받는 사람이 어르신 본인이고 보호자는 같은 가족이라 이름을 넣습니다.
                      앱의 본인 조회와 같습니다. 회원 정보가 없으면 이름이 비어 있습니다
                    - `경로 코드`, `경로`: `REST_LAST`(최근 위치 조회), `REST_TRAIL`(최근 15분 이동 경로 조회),
                      `REST_FAMILY`(가족 어르신 목록에서 위치 조회), `WS_SUBSCRIBE`(실시간 위치 보기 시작),
                      `HOME_OUTING`(홈 화면 외출 여부 확인)
                    - `통보 여부`(`예`/`아니오`), `통보 시각(한국 시간)`

                    **좌표는 없습니다.** `=`·`+`·`-`·`@`로 시작하는 이름은 앞에 `'`를 붙여 수식으로 실행되지 않게 합니다.
                    관리자 접속기록에 관리자·대상 회원이 남습니다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "CSV 파일 (`text/csv`)")
    @ApiResponse(
            responseCode = "400",
            description = "기간 오류(`from >= to`, 1년 초과) 또는 `from`·`to` 누락·형식 오류",
            content = @Content(examples = @ExampleObject(value = """
                    {"code": "LOCATION_4001", "message": "열람 기록 내보내기 기간이 올바르지 않습니다.", "data": null}
                    """))
    )
    @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(examples = @ExampleObject(value = """
                    {"code": "MEMBER_4041", "message": "회원을 찾을 수 없습니다.", "data": null}
                    """))
    )
    ResponseEntity<byte[]> export(
            @Parameter(description = "어르신 회원 ID", example = "28") Long memberId,
            @Parameter(description = "시작 시각, 한국 시간", example = "2026-08-01T00:00:00") LocalDateTime from,
            @Parameter(description = "끝 시각, 한국 시간", example = "2026-11-01T00:00:00") LocalDateTime to);
}
