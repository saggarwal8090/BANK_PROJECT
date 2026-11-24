package com.los.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Enterprise Role-Based Access Control (RBAC) System for Mini LOS.
 */
public enum UserRole {

    LOAN_OFFICER(EnumSet.of(
            Permission.CUSTOMER_CREATE,
            Permission.CUSTOMER_READ,
            Permission.APPLICATION_READ,
            Permission.APPLICATION_DRAFT_CREATE,
            Permission.APPLICATION_DRAFT_EDIT,
            Permission.APPLICATION_SUBMIT,
            Permission.REPORTS_VIEW
    )),

    CREDIT_OFFICER(EnumSet.of(
            Permission.CUSTOMER_READ,
            Permission.APPLICATION_READ,
            Permission.APPLICATION_REVIEW,
            Permission.REPORTS_VIEW,
            Permission.AUDIT_LOG_VIEW
    )),

    DISBURSEMENT_OFFICER(EnumSet.of(
            Permission.CUSTOMER_READ,
            Permission.APPLICATION_READ,
            Permission.APPLICATION_DISBURSE,
            Permission.REPORTS_VIEW,
            Permission.AUDIT_LOG_VIEW
    )),

    AUDITOR(EnumSet.of(
            Permission.CUSTOMER_READ,
            Permission.APPLICATION_READ,
            Permission.REPORTS_VIEW,
            Permission.AUDIT_LOG_VIEW
    )),

    ADMIN(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    UserRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public Set<Permission> getPermissions() {
        return EnumSet.copyOf(permissions);
    }

    public static UserRole fromString(String val) {
        if (val == null || val.trim().isEmpty()) {
            return LOAN_OFFICER; // Default safe fallback
        }
        for (UserRole role : values()) {
            if (role.name().equalsIgnoreCase(val.trim())) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown User Role: " + val);
    }
}
