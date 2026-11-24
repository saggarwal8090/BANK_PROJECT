package com.los.model;

import java.time.OffsetDateTime;

/**
 * Audit log record for background utility executions.
 */
public class UtilityRun {

    private Long runId;
    private String utilityName;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private UtilityResult result;
    private int applicationsProcessed;
    private int alertsCreated;
    private String errorMessage;

    public UtilityRun() {
    }

    public UtilityRun(String utilityName, OffsetDateTime startedAt) {
        this.utilityName = utilityName;
        this.startedAt = startedAt;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public String getUtilityName() {
        return utilityName;
    }

    public void setUtilityName(String utilityName) {
        this.utilityName = utilityName;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public UtilityResult getResult() {
        return result;
    }

    public void setResult(UtilityResult result) {
        this.result = result;
    }

    public int getApplicationsProcessed() {
        return applicationsProcessed;
    }

    public void setApplicationsProcessed(int applicationsProcessed) {
        this.applicationsProcessed = applicationsProcessed;
    }

    public int getAlertsCreated() {
        return alertsCreated;
    }

    public void setAlertsCreated(int alertsCreated) {
        this.alertsCreated = alertsCreated;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "UtilityRun{" +
                "runId=" + runId +
                ", utilityName='" + utilityName + '\'' +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                ", result=" + result +
                ", applicationsProcessed=" + applicationsProcessed +
                ", alertsCreated=" + alertsCreated +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
