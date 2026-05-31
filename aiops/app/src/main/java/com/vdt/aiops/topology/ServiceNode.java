package com.vdt.aiops.topology;

import com.vdt.aiops.topology.enums.ServiceRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/* Each node in graph is a service */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceNode {
    private String name;
    private String containerId;
    private ServiceRole role;
} 
