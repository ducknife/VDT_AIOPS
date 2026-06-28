package com.vdt.aiops.agent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.vdt.aiops.agent.context.ContextBuilder;
import com.vdt.aiops.agent.context.ContextBundle;
import com.vdt.aiops.agent.event.IncidentDiagnosedEvent;
import com.vdt.aiops.agent.event.InvestigationFailedEvent;
import com.vdt.aiops.agent.event.start.AlertView;
import com.vdt.aiops.agent.event.start.InvestigationStartedEvent;
import com.vdt.aiops.agent.incident.Incident;
import com.vdt.aiops.agent.incident.IncidentReport;
import com.vdt.aiops.agent.loop.Query;
import com.vdt.aiops.config.properties.AiopsProperties;
import com.vdt.aiops.monitoring.alertmanager.AlertGroup;
import com.vdt.aiops.utils.SaveIncidentReport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/* Async orchestrator: build context → run agent loop → persist incident.
   Caps concurrency via semaphore; on failure bumps attempts → ACTIVE/FAILED. */

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDispatcher {

    private final ContextBuilder contextBuilder;
    private final Query agentLoop;
    private final SaveIncidentReport saveIncidentReport;
    private final AiopsProperties aiopsProperties;
    private final Semaphore investigationSemaphore;
    private final ApplicationEventPublisher eventPublisher;

    @Async("investigationExecutor")
    public void investigate(AlertGroup group) {
        String root = group.getRootCandidate();
        String investigationId = UUID.randomUUID().toString(); // id for this turn
        long t0 = System.currentTimeMillis();

        // publish start event
        eventPublisher.publishEvent(
                new InvestigationStartedEvent(investigationId, root, alertViews(group)));
        try {
            ContextBundle bundle = contextBuilder.build(group);
            // // take 1 of N permits — parks here only if all N investigations are busy
            investigationSemaphore.acquire(); // acquire OUTSIDE the inner try: only enter (and thus only release) once
                                              // a permit is held
                                              // tránh lỗi: nếu mà để khóa trong try mà try lỗi -> khóa hỏng, release
                                              // cho một permit chưa từng có khóa => sai
            try {
                List<IncidentReport> reports = agentLoop.investigate(bundle, investigationId);
                long ms = System.currentTimeMillis() - t0;
                List<Incident> saved = saveIncidentReport.persist(reports, group.getAlerts(), ms, investigationId);
                // after persist, publish event
                eventPublisher.publishEvent(
                        new IncidentDiagnosedEvent(investigationId, root, saved));
                log.info("Group[root={}] -> {} incident(s) in {}ms", root, reports.size(), ms);
            } finally {
                // always release (even on error) — a leaked permit eventually deadlocks all
                // investigations
                investigationSemaphore.release();
            }
        } catch (InterruptedException e) {
            log.warn("investigate interrupted before running!, group[root={}]", root);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            /* if investigate failed -> call mark failure */
            List<Long> ids = group.getAlerts().stream().map(a -> a.getId()).toList();
            saveIncidentReport.markFailure(ids, aiopsProperties.getAgent().getMaxAttempts());
            // publish failed event
            eventPublisher.publishEvent(
                    new InvestigationFailedEvent(investigationId, root, e.getMessage()));
            log.error("Investigate FAILED group[root={}]: {}", root, e.getMessage());
        }

    }

    private List<AlertView> alertViews(AlertGroup group) {
        return group.getAlerts().stream()
                .map(a -> AlertView.builder()
                        .id(a.getId())
                        .service(a.getService())
                        .type(a.getType().toString())
                        .build())
                .toList();
    }
}
