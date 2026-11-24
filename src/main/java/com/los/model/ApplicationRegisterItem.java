package com.los.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO for Report 1: Application Register.
 */
public class ApplicationRegisterItem {

    private String applicationNumber;
    private String customerName;
    private String customerMobile;
    private ProductType product;
    private BigDecimal requestedAmount;
    private BigDecimal proposedEmi;
    private ApplicationStatus status;
    private OffsetDateTime submittedAt;
    private String branchCode;
    private String branchName;
    private boolean eligible;

    public ApplicationRegisterItem() {
    }

    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }

    public ProductType getProduct() { return product; }
    public void setProduct(ProductType product) { this.product = product; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public BigDecimal getProposedEmi() { return proposedEmi; }
    public void setProposedEmi(BigDecimal proposedEmi) { this.proposedEmi = proposedEmi; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
}
