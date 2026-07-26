package com.ssc.booking.security;

import com.ssc.booking.config.AppProperties;
import com.ssc.booking.service.ObjectKeySanitizer;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validated view of app.integration.clients. Slots with a blank api-key are dropped (unused
 * config slots); anything else invalid — blank name, missing or unsafe project-folder,
 * duplicate name or key — fails startup rather than running with a half-broken registry.
 * Only client names are ever logged, never keys.
 */
@Component
public class IntegrationClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(IntegrationClientRegistry.class);

    private final AppProperties appProperties;
    private final List<AppProperties.Integration.Client> clients = new ArrayList<>();

    public IntegrationClientRegistry(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    void validate() {
        Set<String> names = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (AppProperties.Integration.Client client : appProperties.getIntegration().getClients()) {
            if (client.getApiKey() == null || client.getApiKey().isBlank()) {
                continue;
            }
            String name = client.getName();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException(
                    "Integration client with an api-key set has no name (app.integration.clients).");
            }
            String folder = client.getProjectFolder();
            if (folder == null || folder.isBlank()
                || !ObjectKeySanitizer.sanitizeObjectSegment(folder).equals(folder)
                || folder.equals(".") || folder.equals("..")) {
                throw new IllegalStateException(
                    "Integration client '" + name + "' has a missing or unsafe project-folder"
                        + " (allowed characters: A-Z a-z 0-9 . _ -).");
            }
            if (!names.add(name)) {
                throw new IllegalStateException("Duplicate integration client name '" + name + "'.");
            }
            if (!keys.add(client.getApiKey())) {
                throw new IllegalStateException(
                    "Integration clients '" + name + "' and another slot share the same api-key.");
            }
            clients.add(client);
        }
        if (clients.isEmpty()) {
            log.info("No integration clients configured; /api/v1/integration/files refuses all requests");
        } else {
            log.info("Loaded {} integration client(s): {}", clients.size(),
                clients.stream().map(AppProperties.Integration.Client::getName).toList());
        }
    }

    public List<AppProperties.Integration.Client> clients() {
        return clients;
    }
}
