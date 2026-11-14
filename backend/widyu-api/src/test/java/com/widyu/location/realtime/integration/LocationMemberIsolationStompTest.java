package com.widyu.location.realtime.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.auth.application.SmsService;
import com.widyu.global.websocket.WsTokenService;
import com.widyu.fcm.application.FcmTransport;
import com.widyu.global.security.ActiveMemberValidator;
import com.widyu.location.SeniorLocation;
import com.widyu.location.realtime.repository.SeniorLocationRepository;
import com.widyu.location.stay.application.SeniorStayStateService;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
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
 * 한 회원의 위치 몰림이 다른 회원의 위치 수신을 막지 않는지 실제 STOMP 수신 경로로 본다(#1048, LLD-0144).
 *
 * <p>회원 A의 첫 위치를 판정 단계(체류 상태 조회)에서만 붙잡고, A가 측정 시각을 늘려 가며 30건을 더 보내는 동안 회원 B가 다른
 * 세션에서 1건을 보낸다. Redis 전체를 멈추는 방식이 아니라 A만 막는다. B의 ACK가 A를 풀기 전에 와야 한다. 수정 전에는 A의
 * 요청이 공용 수신 스레드(최대 16개)를 모두 붙잡아 B가 회원 잠금 대기 시간(3초)이 지나서야 처리됐다.
 *
 * <p>DB는 H2, Redis 최신 위치 저장소는 메모리 대역이다. 이 테스트는 수정 전 커밋에서도 그대로 컴파일되도록 새 API를 쓰지 않는다.
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
@DisplayName("회원별 위치 몰림 격리 STOMP")
class LocationMemberIsolationStompTest {

    private static final String ACK = "/user/queue/location/ack";
    private static final String ERRORS = "/user/queue/errors";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final int A_BURST = 30;
    /** 수정 전에는 회원 잠금 대기 3초가 지나야 B가 처리됐다. 이보다 짧게 잡아 차이를 본다. */
    private static final long B_ACK_LIMIT_MS = 2_000;

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
    @MockBean private SeniorLocationRepository seniorLocationRepository;
    @SpyBean(name = "redisTemplate") private RedisTemplate<String, Object> redisTemplate;
    @SpyBean private SeniorStayStateService stayStateService;

    @Autowired private MemberRepository memberRepository;
    @Autowired private FamilyRepository familyRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired @Qualifier("clientInboundChannel") private ExecutorSubscribableChannel clientInboundChannel;
    @LocalServerPort private int port;

    @Test
    @DisplayName("회원 A의 판정이 붙잡혀 A가 위치를 몰아 보내는 동안에도 회원 B의 위치는 A를 풀기 전에 APPLIED로 처리되고, 풀린 뒤 A의 최신 위치는 가장 늦게 잰 위치다")
    void 한_회원의_몰림이_다른_회원을_막지_않는다() throws Exception {
        // given
        Long memberA = senior();
        Long memberB = senior();
        Map<Long, SeniorLocation> latest = new ConcurrentHashMap<>();
        stubLatestStore(latest);
        CountDownLatch aEntered = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        willAnswer(invocation -> {
            aEntered.countDown();
            releaseA.await(30, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).given(stayStateService).find(memberA);

        try (Harness harness = new Harness()) {
            Client a = harness.connect(memberA);
            Client b = harness.connect(memberB);
            long base = System.currentTimeMillis() - 60_000;
            a.sendFix(memberA, base);
            assertThat(aEntered.await(5, TimeUnit.SECONDS)).isTrue();
            long newestA = base;
            for (int i = 1; i <= A_BURST; i++) {
                newestA = base + i * 1_000L;
                a.sendFix(memberA, newestA);
            }
            TimeUnit.MILLISECONDS.sleep(300); // 수정 전이라면 A의 요청이 수신 스레드에 자리 잡을 시간

            // when
            long sentAt = System.currentTimeMillis();
            long bSeq = b.sendFix(memberB, System.currentTimeMillis());
            JsonNode bAck = b.awaitAck(bSeq, B_ACK_LIMIT_MS);
            long bElapsedMs = System.currentTimeMillis() - sentAt;
            boolean aStillHeld = releaseA.getCount() == 1;
            releaseA.countDown();

            // then
            System.out.printf("[ISOLATION] bAckMs=%d bResult=%s aHeldWhenBAcked=%s%n",
                    bElapsedMs, bAck == null ? null : bAck.get("result").asText(), aStillHeld);
            assertThat(bAck).as("A를 풀기 전 %dms 안에 B의 ACK", B_ACK_LIMIT_MS).isNotNull();
            assertThat(bAck.get("result").asText()).isEqualTo("APPLIED");
            assertThat(aStillHeld).isTrue();

            int aTotal = A_BURST + 1;
            a.awaitSettled(aTotal, 10_000);
            System.out.printf("[ISOLATION] aAcks=%s aErrors=%d%n", a.resultCounts(), a.errors.size());
            assertThat(latest.get(memberA).getMeasuredAtMs()).isEqualTo(newestA);
            assertThat(a.resultOf(a.lastSeq).get("result").asText()).isEqualTo("APPLIED");
            assertThat(a.acks.size() + a.errors.size()).isEqualTo(aTotal);
        }
    }

    private void stubLatestStore(Map<Long, SeniorLocation> latest) {
        given(wsTokenService.validateAndConsume(anyString()))
                .willAnswer(invocation -> Long.parseLong(invocation.getArgument(0, String.class).split("-")[1]));
        given(activeMemberValidator.isActive(anyLong())).willReturn(true);
        given(seniorLocationRepository.findById(anyLong()))
                .willAnswer(invocation -> Optional.ofNullable(latest.get(invocation.<Long>getArgument(0))));
        given(seniorLocationRepository.save(any(SeniorLocation.class))).willAnswer(invocation -> {
            SeniorLocation location = invocation.getArgument(0);
            latest.put(location.getSeniorId(), location);
            return location;
        });
        doReturn(mock(ListOperations.class)).when(redisTemplate).opsForList();
        doReturn(true).when(redisTemplate).expire(anyString(), anyLong(), any());
    }

    private Long senior() {
        int n = SEQ.incrementAndGet();
        Family family = familyRepository.save(Family.createFamily("IS" + String.format("%04d", n)));
        Member senior = memberRepository.save(
                Member.createMember(MemberType.SENIOR, "시니어" + n, "0157" + String.format("%07d", n)));
        seniorProfileRepository.save(SeniorProfile.createSeniorProfile(
                senior, family, "서울", "ISI" + String.format("%04d", n), LocalDate.of(1950, 1, 1)));
        return senior.getId();
    }

    /** 이 테스트에서 연 세션을 정리한다. 구독이 브로커에 등록될 때까지 기다린다. */
    private final class Harness implements AutoCloseable {

        private final AtomicInteger handledUserSubscriptions = new AtomicInteger();
        private final List<StompSession> sessions = new ArrayList<>();
        private final WebSocketStompClient stompClient =
                new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        private final ExecutorChannelInterceptor probe = new ExecutorChannelInterceptor() {
            @Override
            public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                            Exception ex) {
                if (handler instanceof UserDestinationMessageHandler
                        && SimpMessageType.SUBSCRIBE.equals(SimpMessageHeaderAccessor.getMessageType(message.getHeaders()))) {
                    handledUserSubscriptions.incrementAndGet();
                }
            }
        };

        private Harness() {
            clientInboundChannel.addInterceptor(probe);
        }

        private Client connect(Long memberId) throws Exception {
            StompSession session = stompClient
                    .connectAsync("http://localhost:" + port + "/ws/location?token=member-" + memberId + "-s",
                            new WebSocketHttpHeaders(), new StompHeaders(), new StompSessionHandlerAdapter() {
                            })
                    .get(5, TimeUnit.SECONDS);
            sessions.add(session);
            int expected = handledUserSubscriptions.get() + 2;
            Client client = new Client(session);
            long deadline = System.currentTimeMillis() + 5_000;
            while (handledUserSubscriptions.get() < expected && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            assertThat(handledUserSubscriptions.get()).isGreaterThanOrEqualTo(expected);
            return client;
        }

        @Override
        public void close() {
            clientInboundChannel.removeInterceptor(probe);
            sessions.forEach(StompSession::disconnect);
            stompClient.stop();
        }
    }

    private static final class Client {

        private final StompSession session;
        private final Queue<JsonNode> acks = new ConcurrentLinkedQueue<>();
        private final Queue<JsonNode> errors = new ConcurrentLinkedQueue<>();
        private long lastSeq;

        private Client(StompSession session) {
            this.session = session;
            subscribe(ACK, acks);
            subscribe(ERRORS, errors);
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

        private long sendFix(Long memberId, long tsMs) {
            long seq = SEQ.incrementAndGet();
            lastSeq = seq;
            String json = """
                    {"v":2,"memberId":%d,"device_id":"ph-%d","session_id":"s-iso","seq":%d,
                     "lat":37.5551,"lon":126.9707,"accuracy_m":8.0,"is_mock":false,"ts_ms":%d,"reason":"move"}"""
                    .formatted(memberId, memberId, seq, tsMs);
            StompHeaders headers = new StompHeaders();
            headers.setDestination("/app/location/update");
            headers.setContentType(MimeTypeUtils.APPLICATION_JSON);
            session.send(headers, json.getBytes(StandardCharsets.UTF_8));
            return seq;
        }

        private JsonNode awaitAck(long seq, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                JsonNode ack = resultOf(seq);
                if (ack != null) {
                    return ack;
                }
                TimeUnit.MILLISECONDS.sleep(10);
            }
            return null;
        }

        private JsonNode resultOf(long seq) {
            for (JsonNode ack : acks) {
                if (ack.path("seq").asLong() == seq) {
                    return ack;
                }
            }
            return null;
        }

        private void awaitSettled(int expected, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (acks.size() + errors.size() < expected && System.currentTimeMillis() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
        }

        private Map<String, Integer> resultCounts() {
            Map<String, Integer> counts = new java.util.TreeMap<>();
            acks.forEach(ack -> counts.merge(ack.path("result").asText(), 1, Integer::sum));
            return counts;
        }
    }
}
