package com.vdt.aiops.monitoring.logcollector;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import com.vdt.aiops.monitoring.logcollector.enums.LogLevel;

@Entity
@Table(name = "container_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String container;
    @Enumerated(EnumType.STRING)
    private LogLevel logLevel;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    private Instant loggedAt;
}
