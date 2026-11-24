package com.los.model;

import java.math.BigDecimal;

/**
 * DTO for Report 3: Disbursement Summary (Daily, Monthly, Quarterly, and Branch views).
 */
public class DisbursementSummaryItem {

    private String periodOrBranch;
    private String secondaryInfo;
    private long totalDisbursements;
    private BigDecimal totalDisbursedAmount;

    public DisbursementSummaryItem() {
    }

    public DisbursementSummaryItem(String periodOrBranch, long totalDisbursements, BigDecimal totalDisbursedAmount) {
        this.periodOrBranch = periodOrBranch;
        this.totalDisbursements = totalDisbursements;
        this.totalDisbursedAmount = totalDisbursedAmount;
    }

    public DisbursementSummaryItem(String periodOrBranch, String secondaryInfo, long totalDisbursements, BigDecimal totalDisbursedAmount) {
        this.periodOrBranch = periodOrBranch;
        this.secondaryInfo = secondaryInfo;
        this.totalDisbursements = totalDisbursements;
        this.totalDisbursedAmount = totalDisbursedAmount;
    }

    public String getPeriodOrBranch() { return periodOrBranch; }
    public void setPeriodOrBranch(String periodOrBranch) { this.periodOrBranch = periodOrBranch; }

    public String getSecondaryInfo() { return secondaryInfo; }
    public void setSecondaryInfo(String secondaryInfo) { this.secondaryInfo = secondaryInfo; }

    public long getTotalDisbursements() { return totalDisbursements; }
    public void setTotalDisbursements(long totalDisbursements) { this.totalDisbursements = totalDisbursements; }

    public BigDecimal getTotalDisbursedAmount() { return totalDisbursedAmount; }
    public void setTotalDisbursedAmount(BigDecimal totalDisbursedAmount) { this.totalDisbursedAmount = totalDisbursedAmount; }
}
