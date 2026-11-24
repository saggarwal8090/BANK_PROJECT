package com.los.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Loan Application domain model.
 */
public class LoanApplication {

    private Long applicationId;
    private String applicationNumber; // APP-YYYY-XXXXXX
    private String customerId;
    private ProductType product;
    private String branchCode;
    private String branchName;
    private BigDecimal requestedAmount;
    private int tenureMonths;
    private BigDecimal monthlyIncome;
    private BigDecimal existingMonthlyObligations;
    private BigDecimal annualInterestRate;
    private BigDecimal proposedEmi;
    private BigDecimal foirPercentage;
    private boolean eligible;
    private String eligibilityReasons;
    private ApplicationStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime submittedAt;
    private OffsetDateTime decidedAt;
    private String reviewerCode;
    private String decisionReason;

    public LoanApplication() {
        this.status = ApplicationStatus.DRAFT;
        this.eligible = false;
        this.existingMonthlyObligations = BigDecimal.ZERO;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public ProductType getProduct() {
        return product;
    }

    public void setProduct(ProductType product) {
        this.product = product;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public int getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(int tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public BigDecimal getExistingMonthlyObligations() {
        return existingMonthlyObligations;
    }

    public void setExistingMonthlyObligations(BigDecimal existingMonthlyObligations) {
        this.existingMonthlyObligations = existingMonthlyObligations;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }

    public BigDecimal getProposedEmi() {
        return proposedEmi;
    }

    public void setProposedEmi(BigDecimal proposedEmi) {
        this.proposedEmi = proposedEmi;
    }

    public BigDecimal getFoirPercentage() {
        return foirPercentage;
    }

    public void setFoirPercentage(BigDecimal foirPercentage) {
        this.foirPercentage = foirPercentage;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public String getEligibilityReasons() {
        return eligibilityReasons;
    }

    public void setEligibilityReasons(String eligibilityReasons) {
        this.eligibilityReasons = eligibilityReasons;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(OffsetDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getReviewerCode() {
        return reviewerCode;
    }

    public void setReviewerCode(String reviewerCode) {
        this.reviewerCode = reviewerCode;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    @Override
    public String toString() {
        return "LoanApplication{" +
                "applicationId=" + applicationId +
                ", applicationNumber='" + applicationNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", product=" + product +
                ", branchCode='" + branchCode + '\'' +
                ", branchName='" + branchName + '\'' +
                ", requestedAmount=" + requestedAmount +
                ", tenureMonths=" + tenureMonths +
                ", monthlyIncome=" + monthlyIncome +
                ", proposedEmi=" + proposedEmi +
                ", foirPercentage=" + foirPercentage +
                ", eligible=" + eligible +
                ", status=" + status +
                '}';
    }
}
