package com.widyu.fcm.dto;

import com.widyu.fcm.NotificationType;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 알림 정책서(위듀 v12, 위듀케어 v18)의 문구표. 코드와 변형은 화면에 노출하지 않는다. */
public record NotificationCopy(String title, String body) {
    /** 메시지 미리보기 최대 글자 수(그래핌). 넘으면 앞 39글자에 「…」을 붙여 40글자를 넘지 않는다(LLD-0167). */
    public static final int PREVIEW_MAX_LENGTH = 40;
    /** 일정명 최대 글자 수(그래핌). 넘으면 앞 15글자에 「…」을 붙인다(정책서 공통 규칙 2). */
    private static final int SCHEDULE_NAME_MAX_LENGTH = 15;
    /** 미리보기가 들어가는 알림센터 본문 칸(fcm_notification.body VARCHAR(255))의 코드포인트 상한. */
    public static final int PREVIEW_MAX_CODE_POINTS = 255;
    /** 이름 자리 상한. 255자까지 허용되는 회원 이름이 들어가도 제목·본문 칸(VARCHAR 255)을 넘지 않게 한다. */
    private static final int NAME_MAX_LENGTH = 20;
    private static final int NAME_MAX_CODE_POINTS = 100;
    private static final Pattern VARIABLE = Pattern.compile("\\{([^}]+)}");
    private static final Pattern CONTROLS = Pattern.compile("[\\p{Cc}&&[^\\s]]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern FORMATS = Pattern.compile("\\p{Cf}");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("a h'시'", Locale.KOREAN);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("a h'시' m'분'", Locale.KOREAN);
    private static final Map<String, NotificationCopy> CATALOG = Map.ofEntries(
            copy("A01", "게시물 업로드가 완료됐어요!", "업로드한 게시물을 확인해보세요."),
            copy("A02-S", "{작성자 이름} 님이 게시물을 올렸어요!", "새로운 게시물을 확인하러 가보세요."),
            copy("A02-C", "{작성자 이름} 님이 새로운 소식을 전했어요!", "새로운 게시물을 확인하러 가보세요."),
            copy("A03", "{작성자 이름} 님이 게시물에 댓글을 달았어요!", "댓글을 확인해보세요."),
            copy("A04", "{작성자 이름} 님이 댓글에 답글을 달았어요!", "답글을 확인해보세요."),
            copy("A05", "{누른 사람 이름} 님이 게시물에 좋아요를 눌렀어요.", null),
            copy("A06-L", "{시니어 이름} 님이 게시물 잠금을 해제했어요.", "아직 잠금을 해제하지 않은 새 소식이 {남은 개수}개 남았어요."),
            copy("A06-Z", "{시니어 이름} 님이 게시물 잠금을 해제했어요.", "아직 잠금을 해제하지 않은 새 소식이 없어요."),
            copy("A06-FALLBACK", "게시물 잠금을 해제했어요.", "앨범에서 확인해보세요."),
            copy("A07", "{시니어 이름} 님이 새 게시물을 모두 확인했어요!", "새로운 소식을 올려주세요."),
            copy("A08", "새 게시물을 열었어요.", "{포인트}P를 사용했어요."),
            copy("M01", "약 복용하실 시간이에요!", null),
            copy("M02", "약 복용 인증이 아직 안 됐어요.", "약을 복용하시기 전에 인증해 주세요."),
            copy("M03", "약 복용 인증 시간이 10분 남았어요.", "지금 약을 복용하시기 전에 인증해 주세요."),
            copy("M04", "{시니어 이름} 님의 약 복용 인증이 아직 확인되지 않았어요.", "약을 드셨는지 직접 확인해보세요."),
            copy("M05", "약 알람이 변경되었습니다.", "이미 인증한 포인트는 사라지지 않아요."),
            copy("M08", "약 알람이 생성되었습니다.", null),
            copy("M09", "약 알람이 삭제되었습니다.", "이미 인증한 포인트는 사라지지 않아요."),
            copy("H01-S-OS", "건강 일정이 곧 있어요.", "앱에서 일정 시간과 내용을 확인해주세요."),
            copy("H01-S-INAPP", "{오전/오후 시각}에 {일정명} 일정이 있어요.", "잊지 않도록 일정을 확인해보세요."),
            copy("H01-C-SELF-OS", "건강 일정이 곧 있어요.", "앱에서 일정 시간과 내용을 확인해주세요."),
            copy("H01-C-SELF-INAPP", "{오전/오후 시각}에 {일정명} 일정이 있어요.", "잊지 않도록 일정을 확인해보세요."),
            copy("H01-C-SENIOR-OS", "{시니어 이름} 님의 건강 일정이 곧 있어요.", "앱에서 일정 시간과 내용을 확인해주세요."),
            copy("H01-C-SENIOR-INAPP", "{오전/오후 시각}에 {시니어 이름} 님의 일정이 있어요.", "{일정명} 일정을 확인해보세요."),
            copy("H02", "{보호자 이름} 님이 새 건강 일정을 등록했어요.", "앱에서 일정을 확인해보세요."),
            copy("H03", "{보호자 이름} 님이 건강 일정을 변경했어요.", "앱에서 바뀐 일정을 확인해보세요."),
            copy("H04", "{보호자 이름} 님이 건강 일정을 삭제했어요.", "앱에서 남은 일정을 확인해보세요."),
            copy("W01", "오늘 {실제 걸음 수}걸음 걸으셨어요.", "가능하다면 조금 더 걸어보는 건 어떨까요?"),
            copy("W02", "{보호자 이름} 님이 걷기 목표를 변경했어요.", "새 목표는 하루 {목표 걸음 수}걸음이에요."),
            copy("G01-S", "{목표명} 목표를 달성했어요!", "{포인트}P가 자동으로 적립됐어요."),
            copy("G01-C", "{시니어 이름} 님이 {목표명} 목표를 달성했어요!", "{포인트}P가 자동으로 적립됐어요."),
            copy("G02-S", "오늘 목표 {달성 개수}개를 달성했어요!", "총 {포인트 합계}P가 자동으로 적립됐어요."),
            copy("G02-C", "{시니어 이름} 님이 목표 {달성 개수}개를 달성했어요!", "총 {포인트 합계}P가 자동으로 적립됐어요."),
            copy("P01", "{포인트}P를 받았어요.", "{적립 사유}"),
            copy("P02", "{포인트}P를 사용했어요.", "{사용 사유}"),
            copy("X01-OS", "{보낸 사람 이름} 님이 메시지를 보냈어요!", "앱에서 메시지를 확인해주세요."),
            copy("X01-INAPP", "{보낸 사람 이름} 님이 메시지를 보냈어요!", "{메시지 미리보기}"),
            copy("X02-OS", "{보낸 사람 이름} 님이 응원메시지를 보냈어요!", "앱에서 응원메시지를 확인해주세요."),
            copy("X02-INAPP", "{보낸 사람 이름} 님이 응원메시지를 보냈어요!", "{메시지 미리보기}"),
            copy("S01", "평소와 다른 심박이 감지됐어요.", "괜찮으시면 밀어서 취소해주세요. 시간 안에 취소하지 않으면 보호자에게 알려드려요."),
            copy("S03", "보호자에게 알렸어요.", "연락을 기다리는 동안 안전한 곳에서 잠시 기다려주세요."),
            copy("S04", "{시니어 이름} 님의 심박 상태를 확인해주세요.", "평소와 다른 심박이 감지됐어요. 현재 상태와 위치를 확인해주세요."),
            copy("S06", "아직 보호자 대응이 확인되지 않았어요.", "{시니어 이름} 님의 심박 상태와 위치를 바로 확인해주세요."),
            copy("S08", "{시니어 이름} 님의 신체 지표가 평소와 달랐어요.", "본인은 괜찮다고 하셨어요. 필요하면 연락해보세요."),
            copy("Z01", "{시니어 이름} 님이 안심구역을 벗어났어요.", null),
            copy("Z02", "{시니어 이름} 님이 안심구역으로 돌아왔어요.", null),
            copy("S10", "괜찮다고 보호자에게 전해드렸어요.", null),
            copy("K01", "{시니어 이름} 님이 워치로 직접 도움을 요청했어요.", null),
            copy("K02", "{시니어 이름} 님이 도움 요청을 취소했어요.", null),
            copy("R02", "{이름} 님이 가족 방장이 되었어요.", null),
            copy("R01", "이제 가족 방장이 되었어요.", "가족 관리와 중요한 알림을 확인해주세요."),
            copy("L01", "{보호자 이름} 님이 내 위치를 확인했어요.", "{오전/오후 시각}에 가족 안부 확인을 위해 위치를 봤어요."),
            copy("L02", "보호자가 내 위치를 확인했어요.", "{시작 날짜}부터 보호자 {보호자 수}명이 가족 안부 확인을 위해 {횟수}회 봤어요."),
            copy("F01", "아까 몸은 괜찮으셨나요?", "짧게 알려주시면 {포인트}포인트를 드려요."),
            copy("F01-NOREWARD", "아까 몸은 괜찮으셨나요?", "짧게 알려주세요."));

    public static NotificationCopy of(NotificationType type, String code, Map<String, String> values) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("알림 문구 코드가 없습니다.");
        }
        if (type == null || type.copyCode() == null || !allowed(type, code)) {
            throw new IllegalArgumentException("알림 타입과 문구 코드가 일치하지 않습니다.");
        }
        NotificationCopy template = CATALOG.get(code);
        if (template == null) {
            throw new IllegalArgumentException("등록되지 않은 알림 문구 코드입니다.");
        }
        Map<String, String> safeValues = Map.of();
        if (values != null) {
            safeValues = values;
        }
        return new NotificationCopy(render(template.title(), safeValues), render(template.body(), safeValues));
    }

    /**
     * 관리자 테스트 발송의 기본 문구 코드. 테스트도 실제 OS 푸시로 나가므로 서버가 보내는 OS 문구를 고른다.
     * H01은 서버가 {@code H01-S-OS}만 보내므로 그 코드로 고정하고(INAPP·보호자 변형은 잠금화면에 일정명이 드러난다),
     * 그 밖에는 {@code -OS} 변형이 있으면 그중 첫 코드, 없으면 사전순 첫 코드를 쓴다.
     */
    public static String defaultCode(NotificationType type) {
        if (type == null || type.copyCode() == null) {
            throw new IllegalArgumentException("문구가 없는 알림 타입입니다.");
        }
        if (type == NotificationType.HEALTH_SCHEDULE_UPCOMING) {
            return "H01-S-OS";
        }
        List<String> codes = CATALOG.keySet().stream()
                .filter(code -> allowed(type, code))
                .sorted()
                .toList();
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("문구가 없는 알림 타입입니다.");
        }
        return codes.stream()
                .filter(code -> code.endsWith("-OS"))
                .findFirst()
                .orElse(codes.getFirst());
    }

    /** 정책서 공통 규칙 3: 「오전/오후 N시 M분」. 정각은 분을 빼고 「오후 3시」로 쓴다(사용자 결정 2026-11-18). */
    public static String clockText(LocalDateTime at) {
        if (at.getMinute() == 0) {
            return at.format(HOUR_FORMATTER);
        }
        return at.format(TIME_FORMATTER);
    }

    /** 문구 코드의 제목·본문에 들어가는 변수 이름. 등록되지 않은 코드면 빈 목록이다. */
    public static Set<String> variables(String code) {
        NotificationCopy template = CATALOG.get(code);
        Set<String> names = new LinkedHashSet<>();
        if (template == null) {
            return names;
        }
        for (String text : new String[] {template.title(), template.body()}) {
            if (text == null) {
                continue;
            }
            Matcher matcher = VARIABLE.matcher(text);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    private static boolean allowed(NotificationType type, String code) {
        if (type == NotificationType.SAFETY_SELF_CHECK) {
            return code.equals("S01");
        }
        if (type == NotificationType.HEART_RATE_EMERGENCY) {
            return code.equals("S01") || code.equals("S03") || code.equals("S04") || code.equals("S06");
        }
        if (type == NotificationType.SAFE_ZONE_EXITED) {
            return code.equals("Z01");
        }
        if (type == NotificationType.SAFE_ZONE_ENTERED) {
            return code.equals("Z02");
        }
        if (type == NotificationType.SAFETY_SENIOR_OK_NOTICE_HEART) {
            return code.equals("S08");
        }
        if (type == NotificationType.FAMILY_LEADER_CHANGED) {
            return code.equals("R01") || code.equals("R02");
        }
        return code.startsWith(type.copyCode().substring(0, 3));
    }

    private static String render(String template, Map<String, String> values) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (key.equals("메시지 미리보기")) {
                value = messagePreview(value);
            }
            if (key.equals("일정명") && value != null) {
                value = truncate(value.strip(), SCHEDULE_NAME_MAX_LENGTH, SCHEDULE_NAME_MAX_LENGTH);
            }
            if ((value == null || value.isBlank()) && key.endsWith("이름")
                    && !key.equals("시니어 이름")) {
                value = values.get("actorDisplayName");
            }
            if ((value == null || value.isBlank()) && key.endsWith("이름")) {
                value = "가족";
            }
            if (key.endsWith("이름")) {
                value = fitCodePoints(truncate(value.strip(), NAME_MAX_LENGTH, NAME_MAX_LENGTH - 1), NAME_MAX_CODE_POINTS);
            }
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("알림 문구 필수 값이 없습니다: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 사용자 메시지를 한 줄로 정리한다(LLD-0167 5.2). NFC로 합치고, 공백이 아닌 제어 문자를 지우고,
     * 줄바꿈과 연속 공백을 공백 하나로 바꾼 뒤 앞뒤 공백을 지운다. 형식 문자(ZWJ 등)는 이모지 조합에 필요해
     * 남기지만 그것만 남으면 빈 값이다.
     */
    public static String normalizeMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        text = CONTROLS.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        if (FORMATS.matcher(text).replaceAll("").isBlank()) {
            return "";
        }
        return text;
    }

    /** 사람이 보는 글자(확장 그래핌 클러스터) 수. Java 20부터 BreakIterator가 이 기준을 따른다. */
    public static int graphemeCount(String text) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int count = 0;
        while (graphemes.next() != BreakIterator.DONE) {
            count++;
        }
        return count;
    }

    /** 정리한 메시지가 40글자를 넘으면 앞 39글자 + 「…」. */
    private static String messagePreview(String raw) {
        return fitCodePoints(truncate(normalizeMessage(raw), PREVIEW_MAX_LENGTH, PREVIEW_MAX_LENGTH - 1),
                PREVIEW_MAX_CODE_POINTS);
    }

    /** {@code maxLength}글자(그래핌)를 넘으면 앞 {@code keepLength}글자 + 「…」. 글자 경계에서만 자른다. */
    private static String truncate(String text, int maxLength, int keepLength) {
        if (graphemeCount(text) <= maxLength) {
            return text;
        }
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        return text.substring(0, graphemes.next(keepLength)).stripTrailing() + "…";
    }

    /**
     * 가족 이모지처럼 한 글자가 코드포인트 여러 개인 글자가 많으면 40글자 미리보기도 본문 칸(255)을 넘을 수 있다.
     * 넘으면 거절하지 않고 칸에 들어갈 때까지 뒤 글자를 줄여 「…」을 붙인다. 글자 하나가 혼자 칸을 넘는 비정상 입력은
     * 그대로 돌려 FcmSendDto.ofMessage의 상한 검사가 400으로 막는다.
     */
    private static String fitCodePoints(String preview, int maxCodePoints) {
        if (preview.codePointCount(0, preview.length()) <= maxCodePoints) {
            return preview;
        }
        String text = preview;
        if (preview.endsWith("…")) {
            text = preview.substring(0, preview.length() - 1);
        }
        BreakIterator graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        int cut = 0;
        int next = graphemes.next();
        while (next != BreakIterator.DONE && text.codePointCount(0, next) <= maxCodePoints - 1) {
            cut = next;
            next = graphemes.next();
        }
        if (cut == 0) {
            return preview;
        }
        return text.substring(0, cut).stripTrailing() + "…";
    }

    private static Entry<String, NotificationCopy> copy(String code, String title, String body) {
        return Map.entry(code, new NotificationCopy(title, body));
    }
}
