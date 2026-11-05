package com.widyu.global.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.auth.application.SmsService;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.properties.SensorProperties;
import com.widyu.global.security.ActiveMemberValidator;
import com.widyu.sensor.application.SensorBatchService;
import com.widyu.sensor.dto.response.SensorBatchResult;
import com.widyu.sensor.dto.response.SensorBatchResultResponse;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 실제 SockJS STOMP 클라이언트로 큰 센서 묶음을 보내 연결이 유지되는지 본다(LLD-0122).
 * 2026-11-05 개발 서버에서 감싼 메시지가 16,640자(STOMP가 올린 세션 상한)를 넘는 묶음이 오면 Tomcat이 closeCode 1009로 세션을 닫았다.
 * 업무 서비스만 대역이고 전송 계층, 브로커, 컨트롤러는 실제 빈이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "firebase.config-path=/dev/null",
        "s3.credentials.access-key=test",
        "s3.credentials.secret-key=test",
        "s3.region.statics=ap-northeast-2",
        "s3.bucket-name=test-bucket",
        "coolsms.api-key=test",
        "coolsms.api-secret=test",
        "coolsms.api-url=https://api.coolsms.co.kr",
        "coolsms.from-phone-number=01000000000",
        "coolsms.verification-code-length=6",
        "coolsms.verification-code-ttl=300",
        "coolsms.message-template=인증번호: {code}"
})
@DisplayName("WebSocket 센서 묶음 크기 통합 테스트")
class WebSocketMessageSizeIntegrationTest {

    private static final long ARRIVAL_TIMEOUT_MS = 5_000L;
    private static final String SENSOR_ACK = "/user/queue/sensor/result";
    private static final String ERRORS = "/user/queue/errors";
    private static final ObjectMapper JSON = new ObjectMapper();

    @MockBean private FcmTransport fcmTransport;
    @MockBean private com.widyu.auth.repository.TemporaryMemberRepository temporaryMemberRepository;
    @MockBean private com.widyu.auth.repository.RefreshTokenRepository refreshTokenRepository;
    @MockBean private com.widyu.auth.infrastructure.AuthLimitStore authLimitStore;
    @MockBean private com.widyu.auth.infrastructure.ClientIpResolver clientIpResolver;
    @MockBean private S3Client s3Client;
    @MockBean private SmsService smsService;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @MockBean private WsTokenService wsTokenService;
    @MockBean private ActiveMemberValidator activeMemberValidator;
    @MockBean private SensorBatchService sensorBatchService;

    @Autowired private SensorProperties sensorProperties;
    @Autowired @Qualifier("websocketSensorExecutor") private ThreadPoolTaskExecutor websocketSensorExecutor;
    @Autowired @Qualifier("clientInboundChannel") private ExecutorSubscribableChannel clientInboundChannel;

    @LocalServerPort private int port;

    @Test
    @DisplayName("16KB가 넘는 센서 묶음을 보내면 연결을 유지하고 저장 ACK를 받는다")
    void 밀린_합본처럼_16KB가_넘는_센서_묶음을_보내면_연결을_유지하고_저장_ACK를_받는다() throws Exception {
        // given: 변경 전 세션 상한 16,640자를 갓 넘는 크기(16,384바이트는 통과, 16,500바이트부터 1009였다)
        stubCollaborators();
        byte[] payload = SensorBatchPayloads.imuBatch("01j8zk3v9x2q4m7n8p1r5s6t71", 1L, 17 * 1024);

        // when
        try (Client client = connect(9101L)) {
            client.send(payload);
            JsonNode ack = client.awaitAck();

            // then
            assertThat(ack).isNotNull();
            assertThat(ack.get("result").asText()).isEqualTo(SensorBatchResult.STORED.name());
            assertThat(client.session.isConnected()).isTrue();
        }
    }

    @Test
    @DisplayName("본문 상한 크기의 센서 묶음을 보내면 SockJS와 STOMP로 감싸도 연결을 유지하고 저장 ACK를 받는다")
    void 본문_상한_크기의_센서_묶음을_보내면_연결을_유지하고_저장_ACK를_받는다() throws Exception {
        // given: 상한을 올리고 버퍼를 그대로 두면 이 테스트가 먼저 깨진다
        stubCollaborators();
        int maxPayloadBytes = sensorProperties.maxPayloadBytes();
        byte[] payload = SensorBatchPayloads.imuBatch("01j8zk3v9x2q4m7n8p1r5s6t72", 2L, maxPayloadBytes);

        // when
        try (Client client = connect(9102L)) {
            client.send(payload);
            JsonNode ack = client.awaitAck();

            // then
            assertThat(payload).hasSize(maxPayloadBytes);
            assertThat(ack).isNotNull();
            assertThat(ack.get("result").asText()).isEqualTo(SensorBatchResult.STORED.name());
            assertThat(client.session.isConnected()).isTrue();
        }
    }

    @Test
    @DisplayName("본문 상한을 넘는 센서 묶음을 보내면 센서 대기열에 넣지 않고 연결을 유지한 채 REJECTED ACK를 받는다")
    void 본문_상한을_넘는_센서_묶음을_보내면_대기열에_넣지_않고_REJECTED_ACK를_받는다() throws Exception {
        // given: 자이로가 켜진 20초 합본(가속도와 자이로 각 1,000개, 약 49KB)
        stubCollaborators();
        byte[] payload = SensorBatchPayloads.imuBatch("01j8zk3v9x2q4m7n8p1r5s6t73", 3L, 49 * 1024);
        long queuedBefore = websocketSensorExecutor.getThreadPoolExecutor().getTaskCount();

        // when
        try (Client client = connect(9103L)) {
            client.send(payload);
            JsonNode ack = client.awaitAck();

            // then
            assertThat(ack).isNotNull();
            assertThat(ack.get("batchId").asText()).isEqualTo("01j8zk3v9x2q4m7n8p1r5s6t73");
            assertThat(ack.get("result").asText()).isEqualTo(SensorBatchResult.REJECTED.name());
            assertThat(client.session.isConnected()).isTrue();
            assertThat(websocketSensorExecutor.getThreadPoolExecutor().getTaskCount()).isEqualTo(queuedBefore);
        }
    }

    private void stubCollaborators() {
        // 토큰 문자열 "member-<회원 ID>"를 그 회원으로 소비한다.
        given(wsTokenService.validateAndConsume(anyString()))
                .willAnswer(invocation -> Long.parseLong(invocation.getArgument(0, String.class).split("-")[1]));
        given(activeMemberValidator.isActive(anyLong())).willReturn(true);
        given(sensorBatchService.ingest(anyLong(), any(byte[].class), anyLong()))
                .willAnswer(invocation -> {
                    JsonNode root = JSON.readTree(invocation.getArgument(1, byte[].class));
                    return SensorBatchResultResponse.of(root.get("batch_id").asText(), root.get("seq").asLong(),
                            SensorBatchResult.STORED);
                });
    }

    private Client connect(long memberId) throws Exception {
        WebSocketStompClient stompClient =
                new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        StompSession session = stompClient
                .connectAsync("http://localhost:" + port + "/ws/location?token=member-" + memberId,
                        new WebSocketHttpHeaders(), new StompHeaders(), new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        Client client = new Client(stompClient, session);

        // 구독이 브로커에 등록되기 전에 보낸 요청의 ACK는 버려진다. 수신 채널은 여러 스레드라 고정 대기 대신
        // 사용자 구독 처리가 끝난 것을 확인한다(WebSocketSessionAckIntegrationTest와 같은 방식).
        AtomicInteger handledSubscriptions = new AtomicInteger();
        ExecutorChannelInterceptor subscriptionProbe = new ExecutorChannelInterceptor() {
            @Override
            public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                            Exception ex) {
                if (handler instanceof UserDestinationMessageHandler
                        && SimpMessageType.SUBSCRIBE.equals(SimpMessageHeaderAccessor.getMessageType(message.getHeaders()))) {
                    handledSubscriptions.incrementAndGet();
                }
            }
        };
        clientInboundChannel.addInterceptor(subscriptionProbe);
        try {
            client.subscribe(SENSOR_ACK, client.acks);
            client.subscribe(ERRORS, client.errors);
            long deadline = System.currentTimeMillis() + ARRIVAL_TIMEOUT_MS;
            while (handledSubscriptions.get() < 2 && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
        } finally {
            clientInboundChannel.removeInterceptor(subscriptionProbe);
        }
        assertThat(handledSubscriptions.get()).isEqualTo(2);
        return client;
    }

    /** 한 STOMP 세션과 그 세션이 받은 ACK. */
    private static final class Client implements AutoCloseable {

        private final WebSocketStompClient stompClient;
        private final StompSession session;
        private final Queue<JsonNode> acks = new ConcurrentLinkedQueue<>();
        private final Queue<JsonNode> errors = new ConcurrentLinkedQueue<>();

        private Client(WebSocketStompClient stompClient, StompSession session) {
            this.stompClient = stompClient;
            this.session = session;
        }

        private void subscribe(String destination, Queue<JsonNode> queue) {
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    try {
                        queue.add(JSON.readTree((byte[]) payload));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        }

        private void send(byte[] payload) {
            StompHeaders headers = new StompHeaders();
            headers.setDestination("/app/sensor/batches/send");
            headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
            session.send(headers, payload);
        }

        /** ACK가 오거나, 연결이 끊기거나, 시간이 지나면 돌아온다. ACK가 없으면 null. */
        private JsonNode awaitAck() throws InterruptedException {
            long deadline = System.currentTimeMillis() + ARRIVAL_TIMEOUT_MS;
            while (acks.isEmpty() && errors.isEmpty() && session.isConnected()
                    && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            return acks.peek();
        }

        @Override
        public void close() {
            if (session.isConnected()) {
                session.disconnect();
            }
            stompClient.stop();
        }
    }

    /** 부록 A 형식의 IMU 묶음. 샘플은 숫자 배열이라 감쌀 때 늘어나는 양은 머리 필드의 따옴표에서만 생긴다. */
    static final class SensorBatchPayloads {

        private static final String SAMPLE = "[20,-980,110]";

        private SensorBatchPayloads() {
        }

        /** 정확히 {@code sizeBytes} 바이트인 묶음을 만든다. 남는 자리는 JSON 공백으로 채운다. */
        static byte[] imuBatch(String batchId, long seq, int sizeBytes) {
            String head = "{\"v\":2,\"stream\":\"imu_watch\",\"source\":\"watch\",\"batch_id\":\"" + batchId + "\","
                    + "\"device_id\":\"gw-547323180759df23\",\"session_id\":\"9aeac07f-836a-4c67-bb83-38d7a2cb304b\","
                    + "\"seq\":" + seq + ",\"study_id\":null,\"participation_id\":null,\"run_id\":null,"
                    + "\"clock\":{\"boot_id\":\"b7c1\",\"clock_mapping_id\":\"cm-01\","
                    + "\"anchor_elapsed_ns\":\"993847100000001\",\"anchor_epoch_ms\":1760000000000,\"uncertainty_ms\":2.0},"
                    + "\"gyro\":null,\"trigger\":null,\"gyro_backfill\":false,\"backfill_for\":null,\"resend\":null,"
                    + "\"collection_mode\":\"product\",\"gyro_mode\":\"continuous\",\"on_body\":true,"
                    + "\"wear_state\":null,\"missing_reason\":null,\"watch_battery_pct\":63,"
                    + "\"acc\":{\"fs_hz_requested\":50,\"t0_elapsed_ns\":\"993847112340001\",\"mg\":[";
            String tail = "]}}";
            StringBuilder json = new StringBuilder(sizeBytes).append(head).append(SAMPLE);
            while (json.length() + 1 + SAMPLE.length() + tail.length() <= sizeBytes) {
                json.append(',').append(SAMPLE);
            }
            json.append(tail);
            json.insert(json.length() - 1, " ".repeat(sizeBytes - json.length()));
            return json.toString().getBytes(StandardCharsets.US_ASCII);
        }
    }
}
