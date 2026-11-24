package com.los;

import com.los.model.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApplicationStatusTest {

    @Test
    @DisplayName("Verify valid state transitions")
    void testValidTransitions() {
        assertTrue(ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.UNDER_REVIEW));
        assertTrue(ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.REJECTED));

        assertTrue(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.CREDIT_APPROVAL));
        assertTrue(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.REJECTED));

        assertTrue(ApplicationStatus.CREDIT_APPROVAL.canTransitionTo(ApplicationStatus.DISBURSED));

        assertTrue(ApplicationStatus.DISBURSED.canTransitionTo(ApplicationStatus.CORE_BANKING_PUSH));
        assertTrue(ApplicationStatus.DISBURSED.canTransitionTo(ApplicationStatus.EXIT));

        assertTrue(ApplicationStatus.CORE_BANKING_PUSH.canTransitionTo(ApplicationStatus.EXIT));
    }

    @Test
    @DisplayName("Verify invalid state transitions are rejected")
    void testInvalidTransitions() {
        assertFalse(ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.DISBURSED));
        assertFalse(ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.CREDIT_APPROVAL));
        assertFalse(ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.EXIT));

        assertFalse(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.DISBURSED));
        assertFalse(ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.DRAFT));

        assertFalse(ApplicationStatus.CREDIT_APPROVAL.canTransitionTo(ApplicationStatus.DRAFT));
        assertFalse(ApplicationStatus.CREDIT_APPROVAL.canTransitionTo(ApplicationStatus.UNDER_REVIEW));
    }

    @Test
    @DisplayName("Verify terminal states cannot transition to any other status")
    void testTerminalStates() {
        assertTrue(ApplicationStatus.REJECTED.isTerminal());
        assertTrue(ApplicationStatus.EXIT.isTerminal());

        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertFalse(ApplicationStatus.REJECTED.canTransitionTo(status), "REJECTED is terminal");
            assertFalse(ApplicationStatus.EXIT.canTransitionTo(status), "EXIT is terminal");
        }
    }
}
