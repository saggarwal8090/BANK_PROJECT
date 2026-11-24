package com.los;

import com.los.model.AccessDeniedException;
import com.los.model.Permission;
import com.los.model.UserContext;
import com.los.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoleBasedAccessControlTest {

    @Test
    @DisplayName("Verify LOAN_OFFICER permissions")
    void testLoanOfficerPermissions() {
        UserContext ctx = new UserContext("officer_ramesh", UserRole.LOAN_OFFICER);

        assertTrue(ctx.hasPermission(Permission.CUSTOMER_CREATE));
        assertTrue(ctx.hasPermission(Permission.APPLICATION_DRAFT_CREATE));
        assertTrue(ctx.hasPermission(Permission.APPLICATION_SUBMIT));

        assertFalse(ctx.hasPermission(Permission.APPLICATION_REVIEW));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_DISBURSE));
        assertFalse(ctx.hasPermission(Permission.UTILITIES_EXECUTE));

        assertThrows(AccessDeniedException.class, () -> ctx.checkPermission(Permission.APPLICATION_REVIEW));
    }

    @Test
    @DisplayName("Verify CREDIT_OFFICER permissions")
    void testCreditOfficerPermissions() {
        UserContext ctx = new UserContext("credit_priya", UserRole.CREDIT_OFFICER);

        assertTrue(ctx.hasPermission(Permission.APPLICATION_REVIEW));
        assertTrue(ctx.hasPermission(Permission.APPLICATION_READ));

        assertFalse(ctx.hasPermission(Permission.APPLICATION_DRAFT_CREATE));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_DISBURSE));

        assertDoesNotThrow(() -> ctx.checkPermission(Permission.APPLICATION_REVIEW));
        assertThrows(AccessDeniedException.class, () -> ctx.checkPermission(Permission.APPLICATION_DISBURSE));
    }

    @Test
    @DisplayName("Verify DISBURSEMENT_OFFICER permissions")
    void testDisbursementOfficerPermissions() {
        UserContext ctx = new UserContext("treasury_amit", UserRole.DISBURSEMENT_OFFICER);

        assertTrue(ctx.hasPermission(Permission.APPLICATION_DISBURSE));
        assertTrue(ctx.hasPermission(Permission.APPLICATION_READ));

        assertFalse(ctx.hasPermission(Permission.APPLICATION_DRAFT_CREATE));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_REVIEW));

        assertDoesNotThrow(() -> ctx.checkPermission(Permission.APPLICATION_DISBURSE));
        assertThrows(AccessDeniedException.class, () -> ctx.checkPermission(Permission.APPLICATION_REVIEW));
    }

    @Test
    @DisplayName("Verify AUDITOR is strictly read-only")
    void testAuditorPermissions() {
        UserContext ctx = new UserContext("auditor_neha", UserRole.AUDITOR);

        assertTrue(ctx.hasPermission(Permission.CUSTOMER_READ));
        assertTrue(ctx.hasPermission(Permission.APPLICATION_READ));
        assertTrue(ctx.hasPermission(Permission.REPORTS_VIEW));
        assertTrue(ctx.hasPermission(Permission.AUDIT_LOG_VIEW));

        assertFalse(ctx.hasPermission(Permission.CUSTOMER_CREATE));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_DRAFT_CREATE));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_SUBMIT));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_REVIEW));
        assertFalse(ctx.hasPermission(Permission.APPLICATION_DISBURSE));
        assertFalse(ctx.hasPermission(Permission.UTILITIES_EXECUTE));
    }

    @Test
    @DisplayName("Verify ADMIN has complete unrestricted permissions")
    void testAdminPermissions() {
        UserContext ctx = new UserContext("admin_super", UserRole.ADMIN);

        for (Permission p : Permission.values()) {
            assertTrue(ctx.hasPermission(p), "Admin must have permission: " + p);
            assertDoesNotThrow(() -> ctx.checkPermission(p));
        }
    }
}
