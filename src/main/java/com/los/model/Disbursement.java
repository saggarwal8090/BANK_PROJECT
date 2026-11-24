package com.los.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Disbursement record.
 */
public class Disbursement {

    private Long disbursementId;
    private Long applicationId;
    private String reference;
    private BigDecimal amount;
    private OffsetDateTime disbursedAt;

    public Disbursement() {
    }

    public Disbursement(Long applicationId, String reference, BigDecimal amount, OffsetDateTime disbursedAt) {
        this.applicationId = applicationId;
        this.reference = reference;
        this.amount = amount;
        this.disbursedAt = disbursedAt;
    }

    public Disbursement(Long disbursementId, Long applicationId, String reference, BigDecimal amount, OffsetDateTime disbursedAt) {
        this.disbursementId = disbursementId;
        this.applicationId = applicationId;
        this.reference = reference;
        this.amount = amount;
        this.disbursedAt = disbursedAt;
    }

    public Long getDisbursementId() {
        return disbursementId;
    }

    public void setDisbursementId(Long disbursementId) {
        this.disbursementId = disbursementId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public OffsetDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(OffsetDateTime disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    @Override
    public String toString() {
        return "Disbursement{" +
                "disbursementId=" + disbursementId +
                ", applicationId=" + applicationId +
                ", reference='" + reference + '\'' +
                ", amount=" + amount +
                ", disbursedAt=" + disbursedAt +
                '}';
    }
}
