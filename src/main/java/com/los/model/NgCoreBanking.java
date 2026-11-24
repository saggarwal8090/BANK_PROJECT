package com.los.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Next-Gen Core Banking Integration Record.
 */
public class NgCoreBanking {

    private Long coreId;
    private Long applicationId;
    private String customerId;
    private String customerName;
    private String address;
    private LocalDate dob;
    private String kycDetails;
    private BigDecimal approvedAmount;
    private BigDecimal disbursedAmount;
    private LocalDate disbursedDate;
    private OffsetDateTime syncedAt;

    public NgCoreBanking() {
    }

    public Long getCoreId() {
        return coreId;
    }

    public void setCoreId(Long coreId) {
        this.coreId = coreId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getKycDetails() {
        return kycDetails;
    }

    public void setKycDetails(String kycDetails) {
        this.kycDetails = kycDetails;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public BigDecimal getDisbursedAmount() {
        return disbursedAmount;
    }

    public void setDisbursedAmount(BigDecimal disbursedAmount) {
        this.disbursedAmount = disbursedAmount;
    }

    public LocalDate getDisbursedDate() {
        return disbursedDate;
    }

    public void setDisbursedDate(LocalDate disbursedDate) {
        this.disbursedDate = disbursedDate;
    }

    public OffsetDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(OffsetDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    @Override
    public String toString() {
        return "NgCoreBanking{" +
                "coreId=" + coreId +
                ", applicationId=" + applicationId +
                ", customerId='" + customerId + '\'' +
                ", customerName='" + customerName + '\'' +
                ", approvedAmount=" + approvedAmount +
                ", disbursedAmount=" + disbursedAmount +
                ", disbursedDate=" + disbursedDate +
                '}';
    }
}
