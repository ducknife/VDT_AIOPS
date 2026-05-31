package com.vdt.aiops.topology.enums;

public enum ServiceRole {
    PROXY, /* gate like nginx */
    APP, /* business service like node-api */
    DATASTORE, /* storage like postgre, redis*/
    EXPORTER, /* exporter */
    LOADGEN, /* traffic gen */
    UNKNOWN /* fallback, can't classify */
}
