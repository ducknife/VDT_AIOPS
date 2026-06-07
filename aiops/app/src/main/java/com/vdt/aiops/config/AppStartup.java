package com.vdt.aiops.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.RequiredArgsConstructor;

/* Enable Scheduling */
@Configuration
@RequiredArgsConstructor
@EnableScheduling
public class AppStartup {
}
