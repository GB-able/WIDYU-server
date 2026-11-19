package com.widyu.fcm.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.fcm.NotificationType;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationCopyTest {
    @Test
    @DisplayName("문구 코드가 없으면 명시적인 예외가 발생한다")
    void 문구_코드가_없으면_명시적인_예외가_발생한다() {
        // given / when / then
        assertThatThrownBy(() -> NotificationCopy.of(NotificationType.SAFETY_SELF_CHECK, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 문구 코드가 없습니다.");
        assertThatThrownBy(() -> NotificationCopy.of(NotificationType.SAFETY_SELF_CHECK, "  ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("알림 문구 코드가 없습니다.");
    }

    @Test
    @DisplayName("본인확인 타입으로 심박 문구를 만들면 S01을 반환한다")
    void 본인확인_타입으로_심박_문구를_만들면_S01을_반환한다() {
        // given / when
        NotificationCopy heart = NotificationCopy.of(NotificationType.SAFETY_SELF_CHECK, "S01", Map.of());

        // then
        assertThat(heart.title()).isEqualTo("평소와 다른 심박이 감지됐어요.");
        assertThat(heart.body()).isEqualTo("괜찮으시면 밀어서 취소해주세요. 시간 안에 취소하지 않으면 보호자에게 알려드려요.");
        assertThatThrownBy(() -> NotificationCopy.of(NotificationType.SAFETY_SELF_CHECK, "Z01", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("안심구역 Z01과 Z02 문구를 만들면 제목만 반환한다")
    void 안심구역_Z01과_Z02_문구를_만들면_제목만_반환한다() {
        // given / when
        Map<String, String> values = Map.of("시니어 이름", "어머니");
        NotificationCopy exit = NotificationCopy.of(NotificationType.SAFE_ZONE_EXITED, "Z01", values);
        NotificationCopy enter = NotificationCopy.of(NotificationType.SAFE_ZONE_ENTERED, "Z02", values);

        // then
        assertThat(exit.title()).isEqualTo("어머니 님이 안심구역을 벗어났어요.");
        assertThat(enter.title()).isEqualTo("어머니 님이 안심구역으로 돌아왔어요.");
        assertThat(exit.body()).isNull();
        assertThat(enter.body()).isNull();
    }

    @Test
    @DisplayName("보호자 이름 없이 문구를 만들면 가족으로 채운 완성 문장을 반환한다")
    void 보호자_이름_없이_문구를_만들면_가족으로_채운다() {
        // given / when
        NotificationCopy copy = NotificationCopy.of(NotificationType.HEALTH_SCHEDULE_CREATED, "H02", Map.of());

        // then
        assertThat(copy.title()).isEqualTo("가족 님이 새 건강 일정을 등록했어요.");
    }

    @Test
    @DisplayName("약 알람 생성, 변경, 삭제 문구를 만들면 보호자 이름 없이 정책서 문구를 반환한다")
    void 약_알람_생성_변경_삭제_문구는_정책서_문구를_반환한다() {
        // given
        Map<String, String> values = Map.of("보호자 이름", "김보호");

        // when
        NotificationCopy created = NotificationCopy.of(NotificationType.MEDICATION_SCHEDULE_CREATED, "M08", values);
        NotificationCopy changed = NotificationCopy.of(NotificationType.MEDICATION_SCHEDULE_CHANGED, "M05", values);
        NotificationCopy deleted = NotificationCopy.of(NotificationType.MEDICATION_SCHEDULE_DELETED, "M09", values);

        // then
        assertThat(created.title()).isEqualTo("약 알람이 생성되었습니다.");
        assertThat(created.body()).isNull();
        assertThat(changed.title()).isEqualTo("약 알람이 변경되었습니다.");
        assertThat(changed.body()).isEqualTo("이미 인증한 포인트는 사라지지 않아요.");
        assertThat(deleted.title()).isEqualTo("약 알람이 삭제되었습니다.");
        assertThat(deleted.body()).isEqualTo("이미 인증한 포인트는 사라지지 않아요.");
    }

    @Test
    @DisplayName("복약 재알림 문구를 만들면 복용 전에 인증하라는 부제목을 반환한다")
    void 복약_재알림_문구는_복용_전에_인증하라는_부제목을_반환한다() {
        // given / when
        NotificationCopy first = NotificationCopy.of(NotificationType.MEDICATION_REMINDER_10, "M02", Map.of());
        NotificationCopy second = NotificationCopy.of(NotificationType.MEDICATION_REMINDER_20, "M03", Map.of());

        // then
        assertThat(first.title()).isEqualTo("약 복용 인증이 아직 안 됐어요.");
        assertThat(first.body()).isEqualTo("약을 복용하시기 전에 인증해 주세요.");
        assertThat(second.title()).isEqualTo("약 복용 인증 시간이 10분 남았어요.");
        assertThat(second.body()).isEqualTo("지금 약을 복용하시기 전에 인증해 주세요.");
    }

    @Test
    @DisplayName("15글자 일정명으로 앱 안 문구를 만들면 자르지 않고 그대로 반환한다")
    void 열다섯글자_일정명은_그대로_반환한다() {
        // given
        String name = "가".repeat(15);

        // when
        NotificationCopy copy = scheduleCopy(name);

        // then
        assertThat(copy.title()).isEqualTo("오후 3시 30분에 " + name + " 일정이 있어요.");
    }

    @Test
    @DisplayName("16글자 일정명으로 앱 안 문구를 만들면 앞 15글자에 말줄임표를 붙인다")
    void 열여섯글자_일정명은_앞_15글자와_말줄임표를_반환한다() {
        // given
        String name = "서울대학교병원 정형외과 정기검진";

        // when
        NotificationCopy copy = scheduleCopy(name);
        NotificationCopy guardian = NotificationCopy.of(NotificationType.HEALTH_SCHEDULE_UPCOMING,
                "H01-C-SENIOR-INAPP", Map.of("오전/오후 시각", "오후 3시 30분", "시니어 이름", "박춘자", "일정명", name));

        // then
        assertThat(copy.title()).isEqualTo("오후 3시 30분에 서울대학교병원 정형외과 정기… 일정이 있어요.");
        assertThat(guardian.body()).isEqualTo("서울대학교병원 정형외과 정기… 일정을 확인해보세요.");
    }

    @Test
    @DisplayName("일정명 15번째 글자가 결합 이모지면 반으로 자르지 않고 한 글자로 센다")
    void 일정명의_결합_이모지를_반으로_자르지_않는다() {
        // given
        String family = "👨‍👩‍👧";
        String name = "가".repeat(14) + family + "끝";

        // when
        NotificationCopy copy = scheduleCopy(name);

        // then
        assertThat(copy.title()).isEqualTo("오후 3시 30분에 " + "가".repeat(14) + family + "… 일정이 있어요.");
    }

    @Test
    @DisplayName("잠금화면 문구를 만들면 일정명과 메시지 원문을 노출하지 않는다")
    void 잠금화면_문구를_만들면_민감한_상세를_노출하지_않는다() {
        // given
        Map<String, String> values = Map.of("일정명", "민감한 진료", "메시지 미리보기", "개인 메시지 원문");

        // when
        NotificationCopy schedule = NotificationCopy.of(NotificationType.HEALTH_SCHEDULE_UPCOMING,
                "H01-S-OS", values);
        NotificationCopy message = NotificationCopy.of(NotificationType.HEART_MESSAGE_RECEIVED,
                "X01-OS", values);

        // then
        assertThat(schedule.title() + schedule.body()).doesNotContain("민감한 진료");
        assertThat(message.title() + message.body()).doesNotContain("개인 메시지 원문");
    }

    @Test
    @DisplayName("40글자 메시지로 앱 안 문구를 만들면 자르지 않고 그대로 반환한다")
    void 마흔글자_메시지는_그대로_반환한다() {
        // given
        String message = "가".repeat(40);

        // when
        String preview = previewOf(message);

        // then
        assertThat(preview).isEqualTo(message);
        assertThat(previewOf("가".repeat(39))).isEqualTo("가".repeat(39));
    }

    @Test
    @DisplayName("41글자 메시지로 앱 안 문구를 만들면 앞 39글자에 말줄임표를 붙여 40글자로 반환한다")
    void 마흔한글자_메시지는_앞_39글자와_말줄임표를_반환한다() {
        // given
        String message = "가".repeat(41);

        // when
        String preview = previewOf(message);

        // then
        assertThat(preview).isEqualTo("가".repeat(39) + "…");
        assertThat(NotificationCopy.graphemeCount(preview)).isEqualTo(40);
    }

    @Test
    @DisplayName("자르는 자리에 이모지가 있으면 이모지를 반으로 자르지 않는다")
    void 자르는_자리의_이모지를_반으로_자르지_않는다() {
        // given
        String family = "👨‍👩‍👧";
        String skinTone = "👍🏽";
        String flag = "🇰🇷";
        String message = "가".repeat(36) + family + skinTone + flag + "😀" + "끝";

        // when
        String preview = previewOf(message);

        // then
        assertThat(NotificationCopy.graphemeCount(message)).isEqualTo(41);
        assertThat(preview).isEqualTo("가".repeat(36) + family + skinTone + flag + "…");
    }

    @Test
    @DisplayName("이모지만 40글자인 메시지는 UTF-16 길이와 상관없이 그대로 반환한다")
    void 이모지_40글자는_그대로_반환한다() {
        // given
        String message = "👨‍👩‍👧".repeat(40);

        // when
        String preview = previewOf(message);

        // then
        assertThat(preview).isEqualTo(message);
    }

    @Test
    @DisplayName("분해된 한글 자모를 보내면 음절로 합쳐 한 글자씩 센다")
    void 분해된_한글을_음절로_합쳐_센다() {
        // given
        String decomposed = java.text.Normalizer.normalize("한글", java.text.Normalizer.Form.NFD);

        // when
        String normalized = NotificationCopy.normalizeMessage(decomposed);

        // then
        assertThat(decomposed).hasSize(6);
        assertThat(normalized).isEqualTo("한글");
        assertThat(NotificationCopy.graphemeCount(normalized)).isEqualTo(2);
    }

    @Test
    @DisplayName("줄바꿈과 연속 공백이 섞인 메시지를 정리하면 공백 하나로 바꾸고 제어 문자와 앞뒤 공백을 지운다")
    void 줄바꿈과_연속_공백을_공백_하나로_정리한다() {
        // given
        String message = "  오늘도\n\n힘내요\r\n\t어머니\u2028사랑해요\u3000\u00A0  ♥\u0000 ";

        // when
        String normalized = NotificationCopy.normalizeMessage(message);

        // then
        assertThat(normalized).isEqualTo("오늘도 힘내요 어머니 사랑해요 ♥");
    }

    @Test
    @DisplayName("공백이나 제로폭 문자만 있는 메시지를 정리하면 빈 값을 반환한다")
    void 공백이나_제로폭_문자만_있으면_빈_값을_반환한다() {
        // given / when / then
        assertThat(NotificationCopy.normalizeMessage("\u3000 \n\t")).isEmpty();
        assertThat(NotificationCopy.normalizeMessage("\u200B\u200D\uFEFF")).isEmpty();
        assertThat(NotificationCopy.normalizeMessage(null)).isEmpty();
    }

    @Test
    @DisplayName("미리보기 값이 정리 뒤 비면 문구를 만들 때 예외가 발생한다")
    void 미리보기가_비면_예외가_발생한다() {
        // given / when / then
        assertThatThrownBy(() -> previewOf("\u3000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다른 이벤트의 문구 코드를 선택하면 예외가 발생한다")
    void 다른_이벤트의_문구를_선택하면_예외가_발생한다() {
        // given / when / then
        assertThatThrownBy(() -> NotificationCopy.of(NotificationType.ALBUM_LIKED, "M05", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("남은 게시물 수를 모르면 숫자 없는 대체 문구를 반환한다")
    void 남은_게시물_수를_모르면_대체_문구를_반환한다() {
        // given / when
        NotificationCopy copy = NotificationCopy.of(NotificationType.ALBUM_UNLOCKED,
                "A06-FALLBACK", Map.of());

        // then
        assertThat(copy.title()).isEqualTo("게시물 잠금을 해제했어요.");
        assertThat(copy.body()).isEqualTo("앨범에서 확인해보세요.");
    }

    private static String previewOf(String message) {
        return NotificationCopy.of(NotificationType.CHEER_MESSAGE_RECEIVED, "X02-INAPP",
                Map.of("보낸 사람 이름", "보호자", "메시지 미리보기", message)).body();
    }

    private static NotificationCopy scheduleCopy(String name) {
        return NotificationCopy.of(NotificationType.HEALTH_SCHEDULE_UPCOMING, "H01-S-INAPP",
                Map.of("오전/오후 시각", "오후 3시 30분", "일정명", name));
    }

    @Test
    @DisplayName("위치 조회 통보 문구를 만들면 보호자 이름, 시각, 목적을 담는다")
    void 위치_조회_통보_문구는_이름_시각_목적을_담는다() {
        // given
        String time = NotificationCopy.clockText(LocalDateTime.of(2026, 11, 20, 15, 20));

        // when
        NotificationCopy copy = NotificationCopy.of(NotificationType.LOCATION_ACCESS_NOTICE, "L01",
                Map.of("보호자 이름", "김보호", "오전/오후 시각", time));

        // then
        assertThat(copy.title()).isEqualTo("김보호 님이 내 위치를 확인했어요.");
        assertThat(copy.body()).isEqualTo("오후 3시 20분에 가족 안부 확인을 위해 위치를 봤어요.");
    }

    @Test
    @DisplayName("이름이 20글자를 넘으면 앞 19글자와 말줄임표로 줄여 제목이 255자를 넘지 않는다")
    void 긴_이름은_줄여서_제목_칸을_넘지_않는다() {
        // given
        String longName = "가".repeat(255);
        String emojiName = "👨‍👩‍👧‍👦".repeat(30);

        // when
        NotificationCopy copy = NotificationCopy.of(NotificationType.LOCATION_ACCESS_NOTICE, "L01",
                Map.of("보호자 이름", longName, "오전/오후 시각", "오후 3시"));
        NotificationCopy emojiCopy = NotificationCopy.of(NotificationType.LOCATION_ACCESS_NOTICE, "L01",
                Map.of("보호자 이름", emojiName, "오전/오후 시각", "오후 3시"));

        // then
        assertThat(copy.title()).isEqualTo("가".repeat(19) + "… 님이 내 위치를 확인했어요.");
        assertThat(emojiCopy.title().codePointCount(0, emojiCopy.title().length())).isLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("시각 문구를 만들면 정각은 분을 빼고 오전, 오후를 붙인다")
    void 시각_문구는_정각이면_분을_뺀다() {
        // given / when / then
        assertThat(NotificationCopy.clockText(LocalDateTime.of(2026, 11, 20, 15, 0))).isEqualTo("오후 3시");
        assertThat(NotificationCopy.clockText(LocalDateTime.of(2026, 11, 20, 9, 5))).isEqualTo("오전 9시 5분");
    }
}
