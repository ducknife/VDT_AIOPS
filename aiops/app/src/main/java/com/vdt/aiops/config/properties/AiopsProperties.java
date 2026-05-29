package com.vdt.aiops.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "aiops")
@Getter
@Setter
public class AiopsProperties {
    private Context context = new Context();
    private Monitoring monitoring = new Monitoring();
    
    @Getter
    @Setter
    public static class Context {
        private Long logWindowMinutes;
    }

    @Getter
    @Setter
    public static class Monitoring {
        private Long pollIntervalMs;
        private String prometheusUrl;
        private List<String> httpMetricsContainers;
    }
}
