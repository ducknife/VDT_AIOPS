package com.vdt.aiops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/* create RestClient to connect http to url */
@Configuration
public class RestConfig {
    
    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
