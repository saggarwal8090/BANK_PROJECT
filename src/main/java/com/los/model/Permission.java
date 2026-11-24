package com.los.model;

/**
 * Granular business permissions for Role-Based Access Control.
 */
public enum Permission {
    CUSTOMER_CREATE,
    CUSTOMER_READ,
    APPLICATION_READ,
    APPLICATION_DRAFT_CREATE,
    APPLICATION_DRAFT_EDIT,
    APPLICATION_SUBMIT,
    APPLICATION_REVIEW,
    APPLICATION_DISBURSE,
    REPORTS_VIEW,
    UTILITIES_EXECUTE,
    AUDIT_LOG_VIEW,
    ADMIN_SETTINGS
}
