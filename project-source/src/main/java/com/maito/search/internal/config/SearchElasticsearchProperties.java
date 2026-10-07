package com.maito.search.internal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "maito.search.elasticsearch")
public class SearchElasticsearchProperties {

    private boolean enabled = true;
    private String uris = "http://localhost:9200";
    private int connectionTimeoutMs = 3000;
    private int socketTimeoutMs = 5000;
}
