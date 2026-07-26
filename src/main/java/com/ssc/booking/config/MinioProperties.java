package com.ssc.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String url;
    private String publicUrl;
    private String accessKey;
    private String secretKey;
    private Buckets buckets = new Buckets();
    private int presignedUrlExpiry;
    private int maxFileSizeMb;
    private int maxProjectFileSizeMb = 25;
    private List<String> blockedExtensions = List.of();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPublicUrl() {
        return publicUrl != null ? publicUrl : url;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Buckets getBuckets() {
        return buckets;
    }

    public void setBuckets(Buckets buckets) {
        this.buckets = buckets;
    }

    public int getPresignedUrlExpiry() {
        return presignedUrlExpiry;
    }

    public void setPresignedUrlExpiry(int presignedUrlExpiry) {
        this.presignedUrlExpiry = presignedUrlExpiry;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public int getMaxProjectFileSizeMb() {
        return maxProjectFileSizeMb;
    }

    public void setMaxProjectFileSizeMb(int maxProjectFileSizeMb) {
        this.maxProjectFileSizeMb = maxProjectFileSizeMb;
    }

    public List<String> getBlockedExtensions() {
        return blockedExtensions;
    }

    public void setBlockedExtensions(List<String> blockedExtensions) {
        this.blockedExtensions = blockedExtensions;
    }

    public static class Buckets {
        private String documents;
        private String templates;
        private String projects;

        public String getDocuments() {
            return documents;
        }

        public void setDocuments(String documents) {
            this.documents = documents;
        }

        public String getTemplates() {
            return templates;
        }

        public void setTemplates(String templates) {
            this.templates = templates;
        }

        public String getProjects() {
            return projects;
        }

        public void setProjects(String projects) {
            this.projects = projects;
        }
    }
}
