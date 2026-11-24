package com.los.model;

import java.time.OffsetDateTime;

/**
 * DTO for Report 5: Customers Without Disbursement.
 */
public class CustomerWithoutDisbursementItem {

    private String customerId;
    private String customerName;
    private String mobile;
    private OffsetDateTime registrationDate;
    private long totalApplications;

    public CustomerWithoutDisbursementItem() {
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }

    public OffsetDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(OffsetDateTime registrationDate) { this.registrationDate = registrationDate; }

    public long getTotalApplications() { return totalApplications; }
    public void setTotalApplications(long totalApplications) { this.totalApplications = totalApplications; }
}
