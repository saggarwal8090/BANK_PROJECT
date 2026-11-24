package com.los.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Strict Finite State Machine for Loan Application Lifecycle.
 *
 * Transitions:
 *  DRAFT -> UNDER_REVIEW (if eligible)
 *  DRAFT -> REJECTED (if ineligible)
 *  UNDER_REVIEW -> CREDIT_APPROVAL (if approved)
 *  UNDER_REVIEW -> REJECTED (if rejected)
 *  CREDIT_APPROVAL -> DISBURSED
 *  DISBURSED -> CORE_BANKING_PUSH
 *  DISBURSED -> EXIT (direct exit upon core push completion)
 *  CORE_BANKING_PUSH -> EXIT
 *
 * Terminal States: REJECTED, EXIT
 */
public enum ApplicationStatus {
    DRAFT,
    UNDER_REVIEW,
    CREDIT_APPROVAL,
    REJECTED,
    DISBURSED,
    CORE_BANKING_PUSH,
    EXIT;

    public boolean isTerminal() {
        return this == REJECTED || this == EXIT;
    }

    public boolean canTransitionTo(ApplicationStatus nextStatus) {
        if (this == nextStatus) {
            return false;
        }
        return switch (this) {
            case DRAFT -> nextStatus == UNDER_REVIEW || nextStatus == REJECTED;
            case UNDER_REVIEW -> nextStatus == CREDIT_APPROVAL || nextStatus == REJECTED;
            case CREDIT_APPROVAL -> nextStatus == DISBURSED;
            case DISBURSED -> nextStatus == CORE_BANKING_PUSH || nextStatus == EXIT;
            case CORE_BANKING_PUSH -> nextStatus == EXIT;
            case REJECTED, EXIT -> false; // Strictly terminal
        };
    }

    public static ApplicationStatus fromString(String val) {
        if (val == null) return null;
        for (ApplicationStatus status : values()) {
            if (status.name().equalsIgnoreCase(val.trim())) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown application status: " + val);
    }
}
