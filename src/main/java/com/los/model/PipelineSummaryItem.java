package com.los.model;

import java.math.BigDecimal;

/**
 * DTO for Report 2: Product & Branch Pipeline.
 */
public class PipelineSummaryItem {

    private String groupKey1; // e.g. Product or Branch Code
    private String groupKey2; // e.g. Status or Branch Name
    private String groupKey3; // e.g. Product
    private long totalApplications;
    private BigDecimal totalRequestedAmount;

    public PipelineSummaryItem() {
    }

    public PipelineSummaryItem(String groupKey1, String groupKey2, long totalApplications, BigDecimal totalRequestedAmount) {
        this.groupKey1 = groupKey1;
        this.groupKey2 = groupKey2;
        this.totalApplications = totalApplications;
        this.totalRequestedAmount = totalRequestedAmount;
    }

    public PipelineSummaryItem(String groupKey1, String groupKey2, String groupKey3, long totalApplications, BigDecimal totalRequestedAmount) {
        this.groupKey1 = groupKey1;
        this.groupKey2 = groupKey2;
        this.groupKey3 = groupKey3;
        this.totalApplications = totalApplications;
        this.totalRequestedAmount = totalRequestedAmount;
    }

    public String getGroupKey1() { return groupKey1; }
    public void setGroupKey1(String groupKey1) { this.groupKey1 = groupKey1; }

    public String getGroupKey2() { return groupKey2; }
    public void setGroupKey2(String groupKey2) { this.groupKey2 = groupKey2; }

    public String getGroupKey3() { return groupKey3; }
    public void setGroupKey3(String groupKey3) { this.groupKey3 = groupKey3; }

    public long getTotalApplications() { return totalApplications; }
    public void setTotalApplications(long totalApplications) { this.totalApplications = totalApplications; }

    public BigDecimal getTotalRequestedAmount() { return totalRequestedAmount; }
    public void setTotalRequestedAmount(BigDecimal totalRequestedAmount) { this.totalRequestedAmount = totalRequestedAmount; }
}
