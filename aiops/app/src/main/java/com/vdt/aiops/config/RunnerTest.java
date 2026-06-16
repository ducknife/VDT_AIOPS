package com.vdt.aiops.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import com.vdt.aiops.agent.context.ContextBuilder;
import com.vdt.aiops.agent.loop.Query;
import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.Alert;
import com.vdt.aiops.monitoring.alertmanager.AlertCorrelator;
import com.vdt.aiops.monitoring.alertmanager.AlertGroup;
import com.vdt.aiops.monitoring.alertmanager.AlertRepository;
import com.vdt.aiops.topology.ServiceGraphBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class RunnerTest implements CommandLineRunner {

    private final AiopsProperties aiopsProperties;
    private final ContextBuilder contextBuilder;
    private final AlertRepository alertRepository;
    private final ServiceGraphBuilder serviceGraphBuilder;
    private final AlertCorrelator alertCorrelator;
    private final Query query;

    @Override
    public void run(String... args) {
        log.info("____Anomaly size: _______" + String.valueOf(aiopsProperties.getAnomaly().getRules().size()));
        List<Alert> active = alertRepository.findByActiveTrue();
        List<AlertGroup> groups = alertCorrelator.correlate(active);

        log.info("==================== CORRELATION RESULT ====================");
        log.info("Active alerts: {}  ->  Groups: {}", active.size(), groups.size());
        int gi = 0;
        for (AlertGroup group : groups) {
            log.info("------------------------------------------------------------");
            log.info("Group #{}  | rootCandidate = {}  | size = {}",
                    gi++, group.getRootCandidate(), group.getAlerts().size());
            for (Alert a : group.getAlerts()) {
                String marker = a.getService().equals(group.getRootCandidate()) ? " <== ROOT" : "";
                log.info("    - {} | {} | {}{}",
                        a.getService(), a.getType(), a.getMessage(), marker);
            }
        }
        log.info("============================================================");

        /* ========= AGENT TEST: gọi investigate() ĐÚNG 1 LẦN =========
         * Bypass dispatch: tự dựng bundle từ group đầu rồi gọi agent.
         * Status INVESTIGATING không ảnh hưởng (findByActiveTrue chỉ lọc active).
         * ⚠️ Mỗi lần khởi động = 1 lần tốn API. Test xong COMMENT khối này lại.
         */
        // if (groups.isEmpty()) {
        //     log.warn("Không có group nào để test agent.");
        //     return;
        // }

        // ContextBundle bundle = contextBuilder.build(groups.get(0));
        // log.info("==================== AGENT INVESTIGATE ====================");
        // try {
        //     List<IncidentReport> reports = query.investigate(bundle);
        //     log.info("==================== REPORTS ({}) ====================", reports.size());
        //     for (IncidentReport r : reports) {
        //         log.info("[{}] {} | {} | rootCause={}",
        //                 r.getSeverity(), r.getService(), r.getTitle(), r.getRootCause());
        //     }
        // } catch (Exception e) {
        //     log.error("Investigate FAILED (rate-limit / parse / API): {}", e.getMessage());  // KHÔNG giết app
        // }
        // log.info("============================================================");
    }
}
