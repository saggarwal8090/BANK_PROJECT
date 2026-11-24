package com.los.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Customer domain model.
 */
public class Customer {

    private String customerId; // CUST-XXXXXX
    private String name;
    private LocalDate dateOfBirth;
    private String mobile; // 10 digits
    private String address;
    private String kycDetails;
    private OffsetDateTime createdAt;

    public Customer() {
    }

    public Customer(String customerId, String name, LocalDate dateOfBirth, String mobile, String address, String kycDetails) {
        this.customerId = customerId;
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.mobile = mobile;
        this.address = address;
        this.kycDetails = kycDetails;
    }

    public Customer(String customerId, String name, LocalDate dateOfBirth, String mobile, String address, String kycDetails, OffsetDateTime createdAt) {
        this.customerId = customerId;
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.mobile = mobile;
        this.address = address;
        this.kycDetails = kycDetails;
        this.createdAt = createdAt;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getKycDetails() {
        return kycDetails;
    }

    public void setKycDetails(String kycDetails) {
        this.kycDetails = kycDetails;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + customerId + '\'' +
                ", name='" + name + '\'' +
                ", dateOfBirth=" + dateOfBirth +
                ", mobile='" + mobile + '\'' +
                ", address='" + address + '\'' +
                ", kycDetails='" + kycDetails + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
