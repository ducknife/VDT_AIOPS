package com.vdt.aiops.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.vdt.aiops.config.properties.DockerProperties;

import lombok.RequiredArgsConstructor;

/* Create docker client for injection of other class */
@Configuration
@RequiredArgsConstructor
public class DockerConfig {
    private final DockerProperties dockerProperties;
    
    @Bean
    public DockerClient dockerClient() {
        /* Create Config (include docker host url) */
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(dockerProperties.getHost())
                    .build();

        /* Http client (connect to docker engine) */
        var httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .maxConnections(50)
                    .connectionTimeout(Duration.ofSeconds(10))
                    .build();

        /* Return DockerClient */
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
