package com.vdt.aiops.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "docker")
@Setter
@Getter
public class DockerProperties {
    private String host;
}
