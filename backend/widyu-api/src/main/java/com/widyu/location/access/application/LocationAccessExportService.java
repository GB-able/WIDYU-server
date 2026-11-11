package com.widyu.location.access.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.dto.response.LocationAccessExportFile;
import com.widyu.location.access.repository.LocationAccessLogRepository;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 어르신 한 분의 위치 열람 기록을 기간으로 CSV로 꺼낸다(LLD-0132). 어르신이 고객센터에 요청하면
 * 본인 확인 뒤 이메일로 보내는 데 쓴다(위치정보 이용약관 제6조).
 *
 * <p>읽기만 한다. 통보 경로({@link LocationAccessLogService})는 건드리지 않는다. 관리자·대상 회원은
 * 관리자 접속기록 인터셉터가 남기고, 읽기라 업무 행위 기록({@code admin_audit_log})은 남기지 않는다(ADR-0036 결정 5).
 * 로그에는 회원 ID와 건수만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationAccessExportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm");

    private final MemberRepository memberRepository;
    private final LocationAccessLogRepository locationAccessLogRepository;

    /** {@code from}·{@code to}는 한국 시간이고 양 끝을 포함한다. 최대 1년. */
    @Transactional(readOnly = true)
    public LocationAccessExportFile export(Long seniorMemberId, LocalDateTime from, LocalDateTime to) {
        if (!from.isBefore(to) || to.isAfter(from.plusYears(1))) {
            throw new BusinessException(ErrorCode.LOCATION_ACCESS_EXPORT_RANGE_INVALID);
        }
        if (!memberRepository.existsById(seniorMemberId)) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        // ponytail: 한 어르신의 1년 기록은 많아야 수만 행이라 한 번에 읽어 메모리에서 만든다. 커지면 스트리밍으로 바꾼다.
        List<LocationAccessLog> logs = locationAccessLogRepository
                .findBySeniorMemberIdAndAccessedAtBetweenOrderByAccessedAtAscIdAsc(seniorMemberId, from, to);
        Map<Long, String> names = viewerNames(logs);

        StringBuilder csv = new StringBuilder(LocationAccessLogCsv.BOM).append(LocationAccessLogCsv.HEADER);
        for (LocationAccessLog accessLog : logs) {
            csv.append(LocationAccessLogCsv.row(accessLog, names.get(accessLog.getViewerMemberId())));
        }

        log.info("위치 열람 기록 내보내기: seniorMemberId={}, rows={}", seniorMemberId, logs.size());
        String fileName = "location_access_member" + seniorMemberId + "_"
                + FILE_TIME.format(from) + "-" + FILE_TIME.format(to) + ".csv";
        return LocationAccessExportFile.of(fileName, csv.toString().getBytes(UTF_8));
    }

    /** 보호자 이름을 한 번에 읽는다. 행마다 조회하면 N+1이 된다. 회원 행이 없으면 이름이 비어 있다. */
    private Map<Long, String> viewerNames(List<LocationAccessLog> logs) {
        List<Long> viewerIds = logs.stream()
                .map(LocationAccessLog::getViewerMemberId)
                .distinct()
                .toList();
        Map<Long, String> names = new HashMap<>();
        if (viewerIds.isEmpty()) {
            return names;
        }
        for (Member viewer : memberRepository.findAllById(viewerIds)) {
            names.put(viewer.getId(), viewer.getName());
        }
        return names;
    }
}
