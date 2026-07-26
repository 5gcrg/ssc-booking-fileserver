package com.ssc.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Integration integration = new Integration();

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public Integration getIntegration() { return integration; }
    public void setIntegration(Integration integration) { this.integration = integration; }

    public static class Jwt {
        private String secret = "dev-secret-key-change-in-production-must-be-at-least-32-characters-long";
        private String cookieName = "ssc_auth_token";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getCookieName() { return cookieName; }
        public void setCookieName(String cookieName) { this.cookieName = cookieName; }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:3000");

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }

    /**
     * External project-storage clients for the X-API-Key-gated /api/v1/integration/files
     * surface. Slots with a blank api-key are ignored, so an empty registry means the
     * integration endpoints refuse all requests.
     */
    public static class Integration {
        private List<Client> clients = new ArrayList<>();
        private RateLimit rateLimit = new RateLimit();

        public List<Client> getClients() { return clients; }
        public void setClients(List<Client> clients) { this.clients = clients; }
        public RateLimit getRateLimit() { return rateLimit; }
        public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

        public static class Client {
            private String name;
            private String apiKey;
            // The single folder under projects/ this client may touch. Comes only from config,
            // never from a request.
            private String projectFolder;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
            public String getProjectFolder() { return projectFolder; }
            public void setProjectFolder(String projectFolder) { this.projectFolder = projectFolder; }
        }

        public static class RateLimit {
            private boolean enabled = true;
            private int capacity = 60;
            private int refillTokens = 60;
            private int refillPeriodSeconds = 60;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getCapacity() { return capacity; }
            public void setCapacity(int capacity) { this.capacity = capacity; }
            public int getRefillTokens() { return refillTokens; }
            public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
            public int getRefillPeriodSeconds() { return refillPeriodSeconds; }
            public void setRefillPeriodSeconds(int refillPeriodSeconds) { this.refillPeriodSeconds = refillPeriodSeconds; }
        }
    }
}
