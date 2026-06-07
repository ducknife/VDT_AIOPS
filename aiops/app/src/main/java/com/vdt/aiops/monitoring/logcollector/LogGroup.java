package com.vdt.aiops.monitoring.logcollector;

import java.time.Instant;

import com.vdt.aiops.monitoring.logcollector.enums.LogLevel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LogGroup {
    private LogLevel level;
    private String pattern;
    private Long count;
    private String raw;
    private Instant firstAt;
    private Instant lastAt;
}
