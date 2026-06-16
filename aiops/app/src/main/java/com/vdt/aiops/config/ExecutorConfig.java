package com.vdt.aiops.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import com.vdt.aiops.config.properties.AiopsProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ExecutorConfig {

    private final AiopsProperties aiopsProperties;
    /* Bounded Concurrency: max 2 thread run concurrency */
    @Bean
    public Executor investigationExecutor() {
        return new VirtualThreadTaskExecutor("duckompose-vt-");
    }

    @Bean
    public Semaphore investigationSemaphore() {
        return new Semaphore(aiopsProperties.getAgent().getMaxConcurrent(), true); // fair = FIFO
    }

    @Bean
    public Executor chatExecutor () {
        return new VirtualThreadTaskExecutor("duckchat-vt");
    }

    @Bean
    public Semaphore chatSemaphore () {
        return new Semaphore(aiopsProperties.getAgent().getMaxConcurrent(), true); // fair = FIFO
    }
}
