package com.los.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO for Report 4 & Background Monitoring: Overdue Reviews.
 */
public class OverdueReviewItem {

    private Long applicationId;
    private String applicationNumber;
    private String customerId;
    private String customerName;
    private String customerMobile;
    private ProductType product;
    private BigDecimal requestedAmount;
    private OffsetDateTime submittedAt;
    private long daysWaiting;

    public OverdueReviewItem() {
    }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerMobile() { return customerMobile; }
    public void setCustomerMobile(String customerMobile) { this.customerMobile = customerMobile; }

    public ProductType getProduct() { return product; }
    public void setProduct(ProductType product) { this.product = product; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }

    public long getDaysWaiting() { return daysWaiting; }
    public void setDaysWaiting(long daysWaiting) { this.daysWaiting = daysWaiting; }
}
