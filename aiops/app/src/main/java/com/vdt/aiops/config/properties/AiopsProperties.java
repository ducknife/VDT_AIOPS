package com.vdt.aiops.config.properties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.vdt.aiops.monitoring.detection.AnomalyRule;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "aiops")
@Getter
@Setter
public class AiopsProperties {
    private Context context = new Context();
    private Monitoring monitoring = new Monitoring();
    private Log log = new Log();
    private Anomaly anomaly = new Anomaly();

    @Getter
    @Setter
    public static class Anomaly {
        private List<AnomalyRule> rules = new ArrayList<>();
        private Long windowSize;
    }
    
    @Getter
    @Setter
    public static class Context {
        private Long logWindowMinutes;
    }

    @Getter
    @Setter
    public static class Log {
        private Long flushIntervalMs;
    }

    @Getter
    @Setter
    public static class Monitoring {
        private Long pollIntervalMs;
        private String prometheusUrl;
    }
}
