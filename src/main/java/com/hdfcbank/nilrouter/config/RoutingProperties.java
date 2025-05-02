package com.hdfcbank.nilrouter.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "routing")
public class RoutingProperties {

    private String header;
    private Map<String, String> mappings;

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Map<String, String> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> mappings) {
        this.mappings = mappings;
    }
}

