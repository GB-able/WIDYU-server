package com.widyu.device.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.device.DeviceEvent;
import com.widyu.device.dto.response.DeviceEventResponse;
import com.widyu.device.dto.response.DeviceEventResult;
import com.widyu.device.repository.DeviceEventRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.MemberRepository;
import com.widyu.run.CollectionRun;
import com.widyu.run.CollectionRunStatus;
import com.widyu.run.application.CollectionRunService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("DeviceEventService 단위 테스트")
class DeviceEventServiceTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Long MEMBER_ID = 1L;
    private static final String WATCH_ID = "gw-3f2a";
    private static final String PHONE_ID = "ph-9c1";
    private static final long OCCURRED_AT_MS = 1_762_650_000_123L;

    @Mock private DeviceEventRepository deviceEventRepository;
    @Mock private CollectionRunService collectionRunService;
    @Mock private MemberRepository memberRepository;

    @Test
    @DisplayName("이벤트 배열을 보내면 항목마다 원문과 해시·종류별 값·회차를 저장하고 요청 순서대로 STORED를 반환한다")
    void 이벤트_배열을_보내면_항목마다_원문과_값을_저장하고_STORED를_반환한다() throws Exception {
        // given
        String wear = """
                {"event_id":"evt-1","device_id":"gw-3f2a","source":"watch","kind":"WEAR_CHANGED",\
                "occurred_at_ms":1762650000123,"session_id":"s-01","attributes":{"on_body":false,"sensor_raw":0},\
                "note":"모르는 필드"}""";
        String network = """
                {"event_id":"evt-2","device_id":"ph-9c1","source":"phone","kind":"NETWORK_CHANGED",\
                "occurred_at_ms":1762650001500,"attributes":{"type":"none"}}""";
        byte[] payload = ("[ " + wear + " ,\n  " + network + " ]").getBytes(UTF_8);
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(collectionRunService.resolveRun(MEMBER_ID, WATCH_ID, OCCURRED_AT_MS)).willReturn(Optional.of(run()));
        given(collectionRunService.resolveRun(MEMBER_ID, PHONE_ID, 1_762_650_001_500L)).willReturn(Optional.empty());
        long before = System.currentTimeMillis();

        // when
        List<DeviceEventResponse> responses = service().ingest(MEMBER_ID, payload);

        // then
        assertThat(responses).containsExactly(
                DeviceEventResponse.of("evt-1", DeviceEventResult.STORED),
                DeviceEventResponse.of("evt-2", DeviceEventResult.STORED));
        List<DeviceEvent> saved = savedEvents(2);
        DeviceEvent first = saved.get(0);
        assertThat(first.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(first.getEventId()).isEqualTo("evt-1");
        assertThat(first.getDeviceId()).isEqualTo(WATCH_ID);
        assertThat(first.getSource()).isEqualTo("watch");
        assertThat(first.getKind()).isEqualTo("WEAR_CHANGED");
        assertThat(first.getOccurredAtMs()).isEqualTo(OCCURRED_AT_MS);
        assertThat(first.getSessionId()).isEqualTo("s-01");
        assertThat(first.getAttributes()).isEqualTo("{\"on_body\":false,\"sensor_raw\":0}");
        assertThat(first.getPayload()).isEqualTo(wear);
        assertThat(first.getPayloadSha256()).isEqualTo(sha256(wear));
        assertThat(first.getRunId()).isEqualTo("run-0f3a");
        assertThat(first.getServerReceivedAtMs()).isGreaterThanOrEqualTo(before);
        DeviceEvent second = saved.get(1);
        assertThat(second.getPayload()).isEqualTo(network);
        assertThat(second.getSessionId()).isNull();
        assertThat(second.getRunId()).isNull();
    }

    @Test
    @DisplayName("같은 기기·이벤트 ID를 다시 보내면 저장하지 않고 DUPLICATE를 반환한다")
    void 같은_기기와_이벤트_ID를_다시_보내면_DUPLICATE를_반환한다() {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(deviceEventRepository.existsByDeviceIdAndEventId(WATCH_ID, "evt-1")).willReturn(true);

        // when
        List<DeviceEventResponse> responses = service().ingest(MEMBER_ID, batch(event("evt-1", "SCREEN_ON")));

        // then
        assertThat(responses).containsExactly(DeviceEventResponse.of("evt-1", DeviceEventResult.DUPLICATE));
        then(deviceEventRepository).should(never()).save(any(DeviceEvent.class));
    }

    @Test
    @DisplayName("UK 경합으로 저장이 실패해도 같은 행이 확인되면 DUPLICATE를 반환한다")
    void UK_경합이어도_같은_행이_확인되면_DUPLICATE를_반환한다() {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(collectionRunService.resolveRun(MEMBER_ID, WATCH_ID, OCCURRED_AT_MS)).willReturn(Optional.empty());
        given(deviceEventRepository.existsByDeviceIdAndEventId(WATCH_ID, "evt-1")).willReturn(false, true);
        willThrow(new DataIntegrityViolationException("uk_device_event"))
                .given(deviceEventRepository).save(any(DeviceEvent.class));

        // when
        List<DeviceEventResponse> responses = service().ingest(MEMBER_ID, batch(event("evt-1", "SCREEN_ON")));

        // then
        assertThat(responses).containsExactly(DeviceEventResponse.of("evt-1", DeviceEventResult.DUPLICATE));
    }

    @Test
    @DisplayName("저장이 실패했는데 같은 행이 없으면 DUPLICATE로 숨기지 않고 예외를 전달한다")
    void 저장_실패에_같은_행이_없으면_예외를_전달한다() {
        // given
        DataIntegrityViolationException failure = new DataIntegrityViolationException("fk_device_event_member");
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(collectionRunService.resolveRun(MEMBER_ID, WATCH_ID, OCCURRED_AT_MS)).willReturn(Optional.empty());
        given(deviceEventRepository.existsByDeviceIdAndEventId(WATCH_ID, "evt-1")).willReturn(false, false);
        willThrow(failure).given(deviceEventRepository).save(any(DeviceEvent.class));

        // when & then
        assertThatThrownBy(() -> service().ingest(MEMBER_ID, batch(event("evt-1", "SCREEN_ON"))))
                .isSameAs(failure);
    }

    @Test
    @DisplayName("형식이 틀린 항목은 그 항목만 REJECTED로 돌려주고 같은 묶음의 다른 항목은 저장한다")
    void 형식이_틀린_항목만_REJECTED하고_나머지는_저장한다() {
        // given
        String missingKind = """
                {"event_id":"evt-2","device_id":"gw-3f2a","source":"watch","occurred_at_ms":1762650000123}""";
        String wrongSource = """
                {"event_id":"evt-3","device_id":"gw-3f2a","source":"tablet","kind":"SCREEN_ON",\
                "occurred_at_ms":1762650000123}""";
        String attributesNotObject = """
                {"event_id":"evt-4","device_id":"gw-3f2a","source":"watch","kind":"SCREEN_ON",\
                "occurred_at_ms":1762650000123,"attributes":"on"}""";
        String oversized = """
                {"event_id":"evt-5","device_id":"gw-3f2a","source":"watch","kind":"SCREEN_ON",\
                "occurred_at_ms":1762650000123,"attributes":{"pad":"%s"}}""".formatted("x".repeat(4096));
        byte[] payload = batch(event("evt-1", "SCREEN_ON"), missingKind, wrongSource, "5", attributesNotObject,
                oversized);
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(collectionRunService.resolveRun(MEMBER_ID, WATCH_ID, OCCURRED_AT_MS)).willReturn(Optional.empty());

        // when
        List<DeviceEventResponse> responses = service().ingest(MEMBER_ID, payload);

        // then
        assertThat(responses).containsExactly(
                DeviceEventResponse.of("evt-1", DeviceEventResult.STORED),
                DeviceEventResponse.of("evt-2", DeviceEventResult.REJECTED),
                DeviceEventResponse.of("evt-3", DeviceEventResult.REJECTED),
                DeviceEventResponse.of(null, DeviceEventResult.REJECTED),
                DeviceEventResponse.of(null, DeviceEventResult.REJECTED),
                DeviceEventResponse.of(null, DeviceEventResult.REJECTED));
        assertThat(savedEvents(1).get(0).getEventId()).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("목록에 없는 종류도 64자 이하면 거절하지 않고 저장한다")
    void 목록에_없는_종류도_저장한다() {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(collectionRunService.resolveRun(MEMBER_ID, WATCH_ID, OCCURRED_AT_MS)).willReturn(Optional.empty());

        // when
        List<DeviceEventResponse> responses =
                service().ingest(MEMBER_ID, batch(event("evt-1", "AOD_CHANGED_FROM_NEW_APP")));

        // then
        assertThat(responses).containsExactly(DeviceEventResponse.of("evt-1", DeviceEventResult.STORED));
        assertThat(savedEvents(1).get(0).getKind()).isEqualTo("AOD_CHANGED_FROM_NEW_APP");
    }

    @Test
    @DisplayName("종류가 64자를 넘으면 그 항목을 REJECTED로 돌려준다")
    void 종류가_64자를_넘으면_REJECTED를_반환한다() {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);

        // when
        List<DeviceEventResponse> responses = service().ingest(MEMBER_ID, batch(event("evt-1", "K".repeat(65))));

        // then
        assertThat(responses).containsExactly(DeviceEventResponse.of("evt-1", DeviceEventResult.REJECTED));
        then(deviceEventRepository).should(never()).save(any(DeviceEvent.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"event_id\":\"evt-1\",\"device_id\":\"gw-3f2a\",\"source\":\"watch\",\"kind\":123,\"occurred_at_ms\":1}",
            "{\"event_id\":\"evt-1\",\"device_id\":\"gw-3f2a\",\"source\":\"watch\",\"kind\":true,\"occurred_at_ms\":1}",
            "{\"event_id\":\"evt-1\",\"device_id\":\"gw-3f2a\",\"source\":1.5,\"kind\":\"SCREEN_ON\",\"occurred_at_ms\":1}",
            "{\"event_id\":\"evt-1\",\"device_id\":\"gw-3f2a\",\"source\":\"watch\",\"kind\":\"SCREEN_ON\",\"occurred_at_ms\":\"1\"}",
            "{\"event_id\":\"evt-1\",\"device_id\":\"gw-3f2a\",\"source\":\"watch\",\"kind\":\"SCREEN_ON\",\"occurred_at_ms\":1.7}"
    })
    @DisplayName("필드 타입이 틀리면 바꿔 읽지 않고 그 항목을 REJECTED로 돌려준다")
    void 필드_타입이_틀리면_REJECTED를_반환한다(String item) {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);

        // when
        List<DeviceEventResponse> responses = service().ingest(MEMBER_ID, batch(item));

        // then
        assertThat(responses).containsExactly(DeviceEventResponse.of(null, DeviceEventResult.REJECTED));
        then(deviceEventRepository).should(never()).save(any(DeviceEvent.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"event_id\":\"evt-1\"}", "[]", "[{\"event_id\":\"evt-1\"}", "[{}] []", "not json"})
    @DisplayName("본문이 배열이 아니거나 비었거나 문법이 깨졌으면 DEVICE_EVENT_BATCH_INVALID 예외가 발생한다")
    void 본문이_배열이_아니거나_비었으면_예외가_발생한다(String body) {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> service().ingest(MEMBER_ID, body.getBytes(UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_EVENT_BATCH_INVALID);
        then(deviceEventRepository).should(never()).save(any(DeviceEvent.class));
    }

    @Test
    @DisplayName("한 요청에 500개를 넘게 보내면 아무것도 저장하지 않고 DEVICE_EVENT_BATCH_INVALID 예외가 발생한다")
    void 한_요청에_500개를_넘으면_예외가_발생한다() {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        String[] events = IntStream.rangeClosed(1, 501)
                .mapToObj(index -> event("evt-" + index, "SCREEN_ON"))
                .toArray(String[]::new);

        // when & then
        assertThatThrownBy(() -> service().ingest(MEMBER_ID, batch(events)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DEVICE_EVENT_BATCH_INVALID);
        then(deviceEventRepository).should(never()).save(any(DeviceEvent.class));
    }

    @Test
    @DisplayName("없는 회원이 보내면 MEMBER_NOT_FOUND 예외가 발생한다")
    void 없는_회원이면_예외가_발생한다() {
        // given
        given(memberRepository.existsById(MEMBER_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> service().ingest(MEMBER_ID, batch(event("evt-1", "SCREEN_ON"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
        then(collectionRunService).should(never()).resolveRun(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("수신 로그에는 회원·기기·건수·결과만 남기고 종류별 값과 원문은 남기지 않는다")
    void 수신_로그에_종류별_값과_원문을_남기지_않는다(CapturedOutput output) {
        // given
        String charging = """
                {"event_id":"evt-1","device_id":"gw-3f2a","source":"watch","kind":"CHARGING_STARTED",\
                "occurred_at_ms":1762650000123,"attributes":{"battery_pct":37}}""";
        given(memberRepository.existsById(MEMBER_ID)).willReturn(true);
        given(collectionRunService.resolveRun(MEMBER_ID, WATCH_ID, OCCURRED_AT_MS)).willReturn(Optional.empty());

        // when
        service().ingest(MEMBER_ID, batch(charging, "5"));

        // then
        assertThat(output).contains("memberId=1", "deviceIds=[gw-3f2a]", "events=2", "STORED=1", "REJECTED=1");
        assertThat(output).doesNotContain("battery_pct", "CHARGING_STARTED");
    }

    private static CollectionRun run() {
        return CollectionRun.builder()
                .runId("run-0f3a")
                .member(Member.createMember(MemberType.SENIOR, "시니어", "01012345678"))
                .startedAtMs(1_762_600_000_000L)
                .status(CollectionRunStatus.OPEN)
                .build();
    }

    private static String event(String eventId, String kind) {
        return """
                {"event_id":"%s","device_id":"%s","source":"watch","kind":"%s","occurred_at_ms":%d}"""
                .formatted(eventId, WATCH_ID, kind, OCCURRED_AT_MS);
    }

    private static byte[] batch(String... events) {
        return Arrays.stream(events).collect(Collectors.joining(",", "[", "]")).getBytes(UTF_8);
    }

    private List<DeviceEvent> savedEvents(int count) {
        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);
        then(deviceEventRepository).should(times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)));
    }

    private DeviceEventService service() {
        return new DeviceEventService(
                deviceEventRepository, collectionRunService, memberRepository, VALIDATOR, new ObjectMapper());
    }
}
