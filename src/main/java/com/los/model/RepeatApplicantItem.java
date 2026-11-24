package com.los.model;

import java.math.BigDecimal;

/**
 * DTO for Report 6: Repeat Applicants.
 */
public class RepeatApplicantItem {

    private String customerId;
    private String customerName;
    private String mobile;
    private long applicationCount;
    private BigDecimal totalRequestedAmount;

    public RepeatApplicantItem() {
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public long getApplicationCount() { return applicationCount; }
    public void setApplicationCount(long applicationCount) { this.applicationCount = applicationCount; }

    public BigDecimal getTotalRequestedAmount() { return totalRequestedAmount; }
    public void setTotalRequestedAmount(BigDecimal totalRequestedAmount) { this.totalRequestedAmount = totalRequestedAmount; }
}
