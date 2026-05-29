package com.vdt.aiops.logcollector;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

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
    private String logLevel;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    private Instant loggedAt;
}
