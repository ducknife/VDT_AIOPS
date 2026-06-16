package com.vdt.aiops.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

/* Enable WebSocket and Open Endpoint /ws/incidents */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WSConfig implements WebSocketConfigurer {

    private final IncidentSocketHandler incidentSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(incidentSocketHandler, "/ws/incidents")
                .setAllowedOrigins("*");
    }
}
