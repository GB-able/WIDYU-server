package com.widyu.location.access.application;

import com.widyu.location.access.LocationAccessLog;
import com.widyu.location.access.LocationAccessPath;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 위치 열람 기록 내보내기의 CSV 행 규칙(LLD-0132 3절). 상태가 없어 칸 구성과 수식 방지를 단위 테스트로 검증한다.
 *
 * <p>감싸기·수식 방지 규칙은 {@code SensorWindowCsv}(LLD-0108)와 같다. 그 파일은 다른 작업이 고치고 있어
 * 공유하지 않고 필요한 만큼만 둔다.
 */
final class LocationAccessLogCsv {

    /** 저장 시각은 서버 기본 시간대(dev·prod Asia/Seoul)의 {@link LocalDateTime}이다. */
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 받는 사람이 엑셀로 열 때 UTF-8 한글이 깨지지 않게 파일 맨 앞에 둔다. */
    static final String BOM = "﻿";

    static final String HEADER = line("열람 시각(한국 시간)", "열람 시각(epoch ms)", "보호자 회원 ID", "보호자 이름",
            "경로 코드", "경로", "통보 여부", "통보 시각(한국 시간)");

    private static final DateTimeFormatter KST_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LocationAccessLogCsv() {
    }

    /** 좌표는 테이블에 없고 넣지도 않는다(LLD-0029). {@code viewerName}이 null이면 빈 칸이다. */
    static String row(LocationAccessLog accessLog, String viewerName) {
        LocalDateTime accessedAt = accessLog.getAccessedAt();
        LocalDateTime notifiedAt = accessLog.getNotifiedAt();
        String notified = "아니오";
        String notifiedText = null;
        if (notifiedAt != null) {
            notified = "예";
            notifiedText = KST_FORMAT.format(notifiedAt);
        }
        return line(KST_FORMAT.format(accessedAt), accessedAt.atZone(KST).toInstant().toEpochMilli(),
                accessLog.getViewerMemberId(), viewerName, accessLog.getPath().name(),
                pathLabel(accessLog.getPath()), notified, notifiedText);
    }

    /** 경로가 늘면 switch가 컴파일되지 않아 설명을 빠뜨릴 수 없다. */
    static String pathLabel(LocationAccessPath path) {
        return switch (path) {
            case REST_LAST -> "최근 위치 조회";
            case REST_TRAIL -> "최근 15분 이동 경로 조회";
            case REST_FAMILY -> "가족 어르신 목록에서 위치 조회";
            case WS_SUBSCRIBE -> "실시간 위치 보기 시작";
            case HOME_OUTING -> "홈 화면 외출 여부 확인";
        };
    }

    static String line(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                line.append(',');
            }
            line.append(escape(values[index]));
        }
        return line.append('\n').toString();
    }

    /** 스프레드시트가 수식으로 읽는 문자로 시작하는 문자열은 앞에 작은따옴표를 붙인다. 보호자 이름은 회원이 입력한 값이다. */
    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (value instanceof CharSequence && startsLikeFormula(text)) {
            text = "'" + text;
        }
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static boolean startsLikeFormula(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
