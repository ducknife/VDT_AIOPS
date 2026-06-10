package com.vdt.aiops.monitoring.metricscraper;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/* Catch json response from prometheus api (instant /query + range /query_range) */
@Getter
@Setter
public class PrometheusResponse {
    private Data data;
    private Result result;

    @Setter
    @Getter
    public static class Data {
        private List<Result> result;
    }

    @Getter
    @Setter
    public static class Result {
        private Map<String, String> metric;   // labels of the series
        private List<Object> value;            // instant query: [ts, "value"]
        private List<List<Object>> values;     // range query: [[ts, "value"], ...]
    }
}
