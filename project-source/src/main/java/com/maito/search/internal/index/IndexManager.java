package com.maito.search.internal.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.PutAliasRequest;
import com.maito.search.internal.config.SearchElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class IndexManager {

    private final SearchElasticsearchProperties properties;

    @Autowired(required = false)
    private ElasticsearchClient elasticsearchClient;

    public void ensureIndexAndAlias(String tenantSlug) {
        if (!properties.isEnabled() || elasticsearchClient == null) {
            log.debug("Elasticsearch is disabled or client not configured. Skipping index initialization.");
            return;
        }

        String safeSlug = (tenantSlug != null && !tenantSlug.isBlank()) ? tenantSlug.trim().toLowerCase() : "mito_crunch";
        String versionedIndex = safeSlug + "_products_v1";
        String aliasName = safeSlug + "_products";

        try {
            boolean exists = elasticsearchClient.indices().exists(ExistsRequest.of(e -> e.index(versionedIndex))).value();
            if (!exists) {
                log.info("Creating Elasticsearch index [{}] with custom edge_ngram settings and alias [{}]", versionedIndex, aliasName);

                ClassPathResource resource = new ClassPathResource("elasticsearch/product-index-settings.json");
                try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    CreateIndexRequest request = CreateIndexRequest.of(c -> c
                            .index(versionedIndex)
                            .withJson(reader)
                    );
                    elasticsearchClient.indices().create(request);
                }

                elasticsearchClient.indices().putAlias(PutAliasRequest.of(a -> a
                        .index(versionedIndex)
                        .name(aliasName)
                ));

                log.info("Successfully provisioned index [{}] with alias [{}]", versionedIndex, aliasName);
            }
        } catch (Exception ex) {
            log.warn("Failed to provision Elasticsearch index [{}] for tenant [{}]: {}", versionedIndex, safeSlug, ex.getMessage());
        }
    }
}
