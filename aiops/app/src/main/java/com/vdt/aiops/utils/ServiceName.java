package com.vdt.aiops.utils;

import com.github.dockerjava.api.model.Container;

public class ServiceName {
    /* get service name use label or container name */
    public static String serviceName(Container c) {
        String s = c.getLabels().get("com.docker.compose.service");
        return s != null ? s : c.getNames()[0].substring(1);
    }
}
