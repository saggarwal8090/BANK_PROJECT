package com.los.model;

import java.time.OffsetDateTime;

/**
 * Enterprise Application User model with assigned UserRole.
 */
public class AppUser {

    private Long userId;
    private String username;
    private String fullName;
    private UserRole role;
    private String email;
    private String branchCode;
    private OffsetDateTime createdAt;

    public AppUser() {
    }

    public AppUser(String username, String fullName, UserRole role, String email, String branchCode) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.email = email;
        this.branchCode = branchCode;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AppUser{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", role=" + role +
                ", email='" + email + '\'' +
                '}';
    }
}
