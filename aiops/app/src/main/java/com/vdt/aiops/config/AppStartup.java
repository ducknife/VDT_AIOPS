package com.vdt.aiops.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vdt.aiops.monitoring.logcollector.DockerLogCollector;

import lombok.RequiredArgsConstructor;

/* Enable Scheduling */
@Configuration
@RequiredArgsConstructor
@EnableScheduling
public class AppStartup {
}
