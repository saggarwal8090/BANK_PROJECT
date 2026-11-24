package com.los.model;

import java.time.OffsetDateTime;

/**
 * Immutable audit trail record for all loan lifecycle events.
 */
public class ApplicationHistory {

    private Long historyId;
    private Long applicationId;
    private String action;
    private ApplicationStatus oldStatus;
    private ApplicationStatus newStatus;
    private String performedBy;
    private OffsetDateTime performedAt;
    private String remarks;

    public ApplicationHistory() {
    }

    public ApplicationHistory(Long applicationId, String action, ApplicationStatus oldStatus, ApplicationStatus newStatus, String performedBy, String remarks) {
        this.applicationId = applicationId;
        this.action = action;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.performedBy = performedBy;
        this.remarks = remarks;
        this.performedAt = OffsetDateTime.now();
    }

    public ApplicationHistory(Long historyId, Long applicationId, String action, ApplicationStatus oldStatus, ApplicationStatus newStatus, String performedBy, OffsetDateTime performedAt, String remarks) {
        this.historyId = historyId;
        this.applicationId = applicationId;
        this.action = action;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
        this.remarks = remarks;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public ApplicationStatus getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(ApplicationStatus oldStatus) {
        this.oldStatus = oldStatus;
    }

    public ApplicationStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(ApplicationStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public OffsetDateTime getPerformedAt() {
        return performedAt;
    }

    public void setPerformedAt(OffsetDateTime performedAt) {
        this.performedAt = performedAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String toString() {
        return "ApplicationHistory{" +
                "historyId=" + historyId +
                ", applicationId=" + applicationId +
                ", action='" + action + '\'' +
                ", oldStatus=" + oldStatus +
                ", newStatus=" + newStatus +
                ", performedBy='" + performedBy + '\'' +
                ", performedAt=" + performedAt +
                ", remarks='" + remarks + '\'' +
                '}';
    }
}
