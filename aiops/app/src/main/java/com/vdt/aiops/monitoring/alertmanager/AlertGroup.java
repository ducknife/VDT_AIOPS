package com.vdt.aiops.monitoring.alertmanager;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Group alerts have same problems, and find the first service to investigate */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertGroup {
    private List<Alert> alerts; 
    private String rootCandidate;
}
