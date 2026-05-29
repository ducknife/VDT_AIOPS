package com.vdt.aiops.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.vdt.aiops.logcollector.DockerLogCollector;

import lombok.RequiredArgsConstructor;

/* Run DockerLogCollector */
@Configuration
@RequiredArgsConstructor
public class AppStartup {
    private final DockerLogCollector logCollector;

    @Bean
    public ApplicationRunner startup() {
        return args -> logCollector.startAll();
    }
}
