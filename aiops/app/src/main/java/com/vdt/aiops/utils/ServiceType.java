package com.vdt.aiops.utils;

// get type of service because some service has the same prefix.
public class ServiceType {

    public static String of(String name) {
        if (name.contains("exporter"))
            return null;
        if (name.contains("traffic") || name.contains("loadgen"))
            return null;
        if (name.contains("nginx"))
            return "nginx";
        if (name.contains("redis"))
            return "redis";
        if (name.contains("postgres") || name.contains("pg"))
            return "postgres";
        if (name.contains("node-api"))
            return "node-api";
        return null;
    }
}
