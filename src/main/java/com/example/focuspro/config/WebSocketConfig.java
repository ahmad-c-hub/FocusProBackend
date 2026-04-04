package com.example.focuspro.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // This interceptor validates the JWT from STOMP CONNECT headers
    // and sets the Principal so @MessageMapping methods know who sent the message
    @Autowired
    private WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker. Clients subscribe to /topic/...
        config.enableSimpleBroker("/topic");
        // Messages sent from clients to /app/... are routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // This is the HTTP endpoint Flutter connects to for the WebSocket upgrade
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Register the JWT interceptor on the inbound channel
        // so it runs before any @MessageMapping method is called
        registration.interceptors(authChannelInterceptor);
    }
}
