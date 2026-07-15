package com.cibertec.gestion_quejas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flag")
public class FeatureFlag {

    @Id
    @Column(name = "key_name")
    private String keyName;

    @Column(nullable = false)
    private boolean enabled;

    private String description;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructores
    public FeatureFlag() {
    }

    public FeatureFlag(String keyName, boolean enabled, String description) {
        this.keyName = keyName;
        this.enabled = enabled;
        this.description = description;
    }

    // Getters y setters
    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}