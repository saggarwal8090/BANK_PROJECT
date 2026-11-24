package com.los.model;

import java.time.OffsetDateTime;

/**
 * System Alert record for monitoring and SLA tracking.
 */
public class SystemAlert {

    private Long alertId;
    private Long applicationId;
    private String alertType;
    private OffsetDateTime createdAt;

    public SystemAlert() {
    }

    public SystemAlert(Long applicationId, String alertType) {
        this.applicationId = applicationId;
        this.alertType = alertType;
        this.createdAt = OffsetDateTime.now();
    }

    public SystemAlert(Long alertId, Long applicationId, String alertType, OffsetDateTime createdAt) {
        this.alertId = alertId;
        this.applicationId = applicationId;
        this.alertType = alertType;
        this.createdAt = createdAt;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "SystemAlert{" +
                "alertId=" + alertId +
                ", applicationId=" + applicationId +
                ", alertType='" + alertType + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
