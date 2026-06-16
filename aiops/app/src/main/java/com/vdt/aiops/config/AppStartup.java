package com.vdt.aiops.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.RequiredArgsConstructor;

/* Enable Scheduling */
@Configuration
@RequiredArgsConstructor
@EnableScheduling
@EnableAsync
public class AppStartup {
}
