package com.vdt.aiops.monitoring.metricscraper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/* label display and promQL */
@Getter
@AllArgsConstructor
@Builder
public class MetricProbe {
    /*
     * name easy to use result, exp: rate(redis_evicted_keys_total[1m]) ->
     * evictedKeysPerSec
     */
    private String label;
    private String promQL;
}
