package com.widyu.global.config;

import com.widyu.global.websocket.FamilyTopicOutboundInterceptor;
import com.widyu.global.websocket.JwtChannelInterceptor;
import com.widyu.global.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** 서버·클라이언트 하트비트 간격(ms). 모바일 망에서 빈 시간에도 연결을 유지한다. */
    private static final long[] HEARTBEAT_MS = {10_000L, 10_000L};

    /**
     * 메시지 한 건의 상한. 센서 본문 상한 32KB에 SockJS와 STOMP로 감싸는 약 221자를 더한 크기와
     * 앱의 최대 합본(약 49KB)이 들어가고, Spring STOMP 기본 상한과 같다(LLD-0122).
     */
    private static final int MESSAGE_SIZE_LIMIT = 64 * 1024;

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final FamilyTopicOutboundInterceptor familyTopicOutboundInterceptor;
    private final ThreadPoolTaskExecutor websocketInboundExecutor;
    private final ThreadPoolTaskExecutor websocketOutboundExecutor;

    private TaskScheduler messageBrokerTaskScheduler;

    /**
     * @EnableWebSocketMessageBroker가 등록한 브로커 스케줄러를 하트비트에 쓴다. 이 설정 클래스가 그 빈보다
     * 먼저 만들어지므로 지연 주입한다. 새 스케줄러 빈을 만들면 TaskScheduler 타입 주입이 모호해진다.
     */
    @Autowired
    public void setMessageBrokerTaskScheduler(@Lazy TaskScheduler messageBrokerTaskScheduler) {
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/location")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 구독할 prefix
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(HEARTBEAT_MS)
                .setTaskScheduler(messageBrokerTaskScheduler);

        // 클라이언트가 메시지 전송할 때 사용할 prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT);
        // 지정하지 않으면 STOMP가 Tomcat 기본 8,192자를 세션마다 16,640자로만 올려, 그보다 큰 메시지가 오면
        // Tomcat이 연결 전체를 닫는다(1009). ServletServerContainerFactoryBean은 서블릿 컨테이너가 없는
        // 테스트 컨텍스트를 깨뜨리므로 세션 단위로 올린다.
        registration.addDecoratorFactory(handler -> new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.setTextMessageSizeLimit(MESSAGE_SIZE_LIMIT);
                super.afterConnectionEstablished(session);
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(websocketInboundExecutor);
        registration.interceptors(jwtChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 지정하지 않으면 Spring Boot가 유일한 실행기였던 수신 실행기를 응답에도 써, 응답이 수신 대기열을 한 번 더 기다렸다(#782).
        registration.taskExecutor(websocketOutboundExecutor);
        registration.interceptors(familyTopicOutboundInterceptor);
    }
}
