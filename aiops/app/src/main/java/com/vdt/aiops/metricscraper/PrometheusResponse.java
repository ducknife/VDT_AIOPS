package com.vdt.aiops.metricscraper;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/* Catch json response from prometheus api */
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
        private List<Object> value;
    }
}
