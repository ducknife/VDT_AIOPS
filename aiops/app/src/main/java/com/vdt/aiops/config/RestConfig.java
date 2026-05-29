package com.vdt.aiops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestConfig {
    @Bean
    public RestClient restClient() {
        
    }

}
