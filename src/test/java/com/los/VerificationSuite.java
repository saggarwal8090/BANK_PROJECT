package com.los;

import com.los.config.IndianCurrencyFormatter;
import com.los.model.ApplicationStatus;
import com.los.model.Customer;
import com.los.model.EligibilityResult;
import com.los.model.LoanApplication;
import com.los.model.ProductType;
import com.los.service.EligibilityRuleEngine;
import com.los.service.FinancialCalculationService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Self-contained executable verification test runner.
 * Runs all financial formulas, state transitions, boundary conditions, and underwriting checks.
 */
public class VerificationSuite {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   RUNNING MINI LOS ENTERPRISE VERIFICATION SUITE (JAVA 21)                     ");
        System.out.println("================================================================================");

        testFinancialEmiFormula();
        testFoirCalculation();
        testCompletedAge();
        testIndianCurrencyFormatter();
        testStateTransitions();
        testTerminalStatuses();
        testEligibilityAgeBoundaries();
        testEligibilityPersonalAmountBounds();
        testEligibilityEducationAmountBounds();
        testEligibilityFoirBoundary();
        testNonShortCircuitingRuleEngine();
        testRoleBasedAccessControl();
        testSupabaseConfiguration();

        System.out.println("================================================================================");
        System.out.printf("   TEST SUITE EXECUTION SUMMARY: %d PASSED | %d FAILED%n", passed, failed);
        System.out.println("================================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFinancialEmiFormula() {
        FinancialCalculationService service = new FinancialCalculationService();
        BigDecimal p = new BigDecimal("100000.00");
        BigDecimal rate = new BigDecimal("12.00");
        int tenure = 12;

        BigDecimal emi = service.calculateEMI(p, rate, tenure);
        BigDecimal expected = new BigDecimal("8884.88");

        assertCondition("EMI Verification (P=100k, r=12%, n=12 -> 8884.88)", expected.compareTo(emi) == 0,
                "Expected " + expected + " but got " + emi);
    }

    private static void testFoirCalculation() {
        FinancialCalculationService service = new FinancialCalculationService();
        BigDecimal oblig = new BigDecimal("10000.00");
        BigDecimal emi = new BigDecimal("8884.88");
        BigDecimal income = new BigDecimal("50000.00");

        BigDecimal foir = service.calculateFOIR(oblig, emi, income);
        BigDecimal expected = new BigDecimal("37.77");

        assertCondition("FOIR Calculation ((10000 + 8884.88) / 50000 * 100 -> 37.77%)", expected.compareTo(foir) == 0,
                "Expected " + expected + " but got " + foir);
    }

    private static void testCompletedAge() {
        FinancialCalculationService service = new FinancialCalculationService();
        LocalDate dob = LocalDate.of(2000, 1, 15);
        LocalDate bDate = LocalDate.of(2026, 1, 15);

        int age = service.calculateAge(dob, bDate);
        assertCondition("Completed Age via Period.between (2000 to 2026 -> 26 years)", age == 26,
                "Expected 26 but got " + age);
    }

    private static void testIndianCurrencyFormatter() {
        String f1 = IndianCurrencyFormatter.format(new BigDecimal("1000000.00"));
        assertCondition("Indian Currency 10 Lakhs -> 10,00,000.00", "10,00,000.00".equals(f1), "Got: " + f1);

        String f2 = IndianCurrencyFormatter.format(new BigDecimal("100000.00"));
        assertCondition("Indian Currency 1 Lakh -> 1,00,000.00", "1,00,000.00".equals(f2), "Got: " + f2);

        String f3 = IndianCurrencyFormatter.format(new BigDecimal("8884.88"));
        assertCondition("Indian Currency 8884.88 -> 8,884.88", "8,884.88".equals(f3), "Got: " + f3);
    }

    private static void testStateTransitions() {
        assertCondition("DRAFT -> UNDER_REVIEW allowed", ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.UNDER_REVIEW));
        assertCondition("DRAFT -> REJECTED allowed", ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.REJECTED));
        assertCondition("UNDER_REVIEW -> CREDIT_APPROVAL allowed", ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.CREDIT_APPROVAL));
        assertCondition("UNDER_REVIEW -> REJECTED allowed", ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.REJECTED));
        assertCondition("CREDIT_APPROVAL -> DISBURSED allowed", ApplicationStatus.CREDIT_APPROVAL.canTransitionTo(ApplicationStatus.DISBURSED));
        assertCondition("DISBURSED -> EXIT allowed", ApplicationStatus.DISBURSED.canTransitionTo(ApplicationStatus.EXIT));

        assertCondition("DRAFT -> DISBURSED rejected", !ApplicationStatus.DRAFT.canTransitionTo(ApplicationStatus.DISBURSED));
        assertCondition("UNDER_REVIEW -> DRAFT rejected", !ApplicationStatus.UNDER_REVIEW.canTransitionTo(ApplicationStatus.DRAFT));
    }

    private static void testTerminalStatuses() {
        assertCondition("REJECTED is terminal", ApplicationStatus.REJECTED.isTerminal());
        assertCondition("EXIT is terminal", ApplicationStatus.EXIT.isTerminal());
        assertCondition("REJECTED cannot transition anywhere", !ApplicationStatus.REJECTED.canTransitionTo(ApplicationStatus.CREDIT_APPROVAL));
        assertCondition("EXIT cannot transition anywhere", !ApplicationStatus.EXIT.canTransitionTo(ApplicationStatus.DRAFT));
    }

    private static void testEligibilityAgeBoundaries() {
        EligibilityRuleEngine engine = new EligibilityRuleEngine();
        LocalDate bDate = LocalDate.of(2026, 11, 24);

        // Age 21
        Customer c21 = createCustomer(bDate.minusYears(21));
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertCondition("Age 21 is eligible", engine.evaluate(c21, app, bDate).isEligible());

        // Age 20
        Customer c20 = createCustomer(bDate.minusYears(21).plusDays(1));
        assertCondition("Age 20 is ineligible", !engine.evaluate(c20, app, bDate).isEligible());

        // Age 60
        Customer c60 = createCustomer(bDate.minusYears(60));
        assertCondition("Age 60 is eligible", engine.evaluate(c60, app, bDate).isEligible());

        // Age 61
        Customer c61 = createCustomer(bDate.minusYears(61));
        assertCondition("Age 61 is ineligible", !engine.evaluate(c61, app, bDate).isEligible());

        // Future DOB
        Customer cFuture = createCustomer(bDate.plusDays(1));
        assertCondition("Future DOB is rejected", !engine.evaluate(cFuture, app, bDate).isEligible());
    }

    private static void testEligibilityPersonalAmountBounds() {
        EligibilityRuleEngine engine = new EligibilityRuleEngine();
        LocalDate bDate = LocalDate.of(2026, 11, 24);
        Customer c = createCustomer(bDate.minusYears(30));

        // 49,999 -> Fail
        LoanApplication a1 = createApplication(ProductType.PERSONAL, new BigDecimal("49999.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertCondition("Personal amount 49,999 fails", !engine.evaluate(c, a1, bDate).isEligible());

        // 50,000 -> Pass
        LoanApplication a2 = createApplication(ProductType.PERSONAL, new BigDecimal("50000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertCondition("Personal amount 50,000 passes", engine.evaluate(c, a2, bDate).isEligible());

        // 500,000 -> Pass
        LoanApplication a3 = createApplication(ProductType.PERSONAL, new BigDecimal("500000.00"), 60, new BigDecimal("100000.00"), BigDecimal.ZERO);
        assertCondition("Personal amount 500,000 passes", engine.evaluate(c, a3, bDate).isEligible());

        // 500,001 -> Fail
        LoanApplication a4 = createApplication(ProductType.PERSONAL, new BigDecimal("500001.00"), 60, new BigDecimal("100000.00"), BigDecimal.ZERO);
        assertCondition("Personal amount 500,001 fails", !engine.evaluate(c, a4, bDate).isEligible());
    }

    private static void testEligibilityEducationAmountBounds() {
        EligibilityRuleEngine engine = new EligibilityRuleEngine();
        LocalDate bDate = LocalDate.of(2026, 11, 24);
        Customer c = createCustomer(bDate.minusYears(25));

        // 99,999 -> Fail
        LoanApplication a1 = createApplication(ProductType.EDUCATION, new BigDecimal("99999.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertCondition("Education amount 99,999 fails", !engine.evaluate(c, a1, bDate).isEligible());

        // 100,000 -> Pass
        LoanApplication a2 = createApplication(ProductType.EDUCATION, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertCondition("Education amount 100,000 passes", engine.evaluate(c, a2, bDate).isEligible());

        // 1,000,000 with 84m tenure -> Pass
        LoanApplication a3 = createApplication(ProductType.EDUCATION, new BigDecimal("1000000.00"), 84, new BigDecimal("80000.00"), BigDecimal.ZERO);
        assertCondition("Education amount 1,000,000 passes", engine.evaluate(c, a3, bDate).isEligible());

        // 85m tenure -> Fail
        LoanApplication a4 = createApplication(ProductType.EDUCATION, new BigDecimal("500000.00"), 85, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertCondition("Education tenure 85m fails", !engine.evaluate(c, a4, bDate).isEligible());
    }

    private static void testEligibilityFoirBoundary() {
        EligibilityRuleEngine engine = new EligibilityRuleEngine();
        LocalDate bDate = LocalDate.of(2026, 11, 24);
        Customer c = createCustomer(bDate.minusYears(30));

        // Income = 20,000. Personal loan 50,000, 12m, 12%. EMI = 4,442.44
        // If obligations = 5,557.56 -> Total = 10,000.00 -> FOIR = 50.00% -> Pass
        LoanApplication aPass = createApplication(ProductType.PERSONAL, new BigDecimal("50000.00"), 12, new BigDecimal("20000.00"), new BigDecimal("5557.56"));
        assertCondition("FOIR exactly 50.00% passes", engine.evaluate(c, aPass, bDate).isEligible());

        // FOIR > 50%
        LoanApplication aFail = createApplication(ProductType.PERSONAL, new BigDecimal("50000.00"), 12, new BigDecimal("20000.00"), new BigDecimal("6000.00"));
        assertCondition("FOIR > 50.00% fails", !engine.evaluate(c, aFail, bDate).isEligible());
    }

    private static void testNonShortCircuitingRuleEngine() {
        EligibilityRuleEngine engine = new EligibilityRuleEngine();
        LocalDate bDate = LocalDate.of(2026, 11, 24);
        // Violates 4 rules simultaneously
        Customer c = createCustomer(bDate.minusYears(18)); // Age failure
        LoanApplication a = createApplication(ProductType.PERSONAL, new BigDecimal("10000.00"), 6, new BigDecimal("5000.00"), new BigDecimal("10000.00"));
        // Amount failure, tenure failure, income failure, FOIR failure

        EligibilityResult res = engine.evaluate(c, a, bDate);
        assertCondition("Non-short-circuiting rule engine collects all failures", res.getReasons().size() >= 4,
                "Expected >= 4 reasons, but got " + res.getReasons().size() + ": " + res.getReasons());
    }

    private static void testRoleBasedAccessControl() {
        com.los.model.UserContext loanOfficer = new com.los.model.UserContext("officer_ramesh", com.los.model.UserRole.LOAN_OFFICER);
        com.los.model.UserContext creditOfficer = new com.los.model.UserContext("credit_priya", com.los.model.UserRole.CREDIT_OFFICER);
        com.los.model.UserContext treasuryOfficer = new com.los.model.UserContext("treasury_amit", com.los.model.UserRole.DISBURSEMENT_OFFICER);
        com.los.model.UserContext auditor = new com.los.model.UserContext("auditor_neha", com.los.model.UserRole.AUDITOR);
        com.los.model.UserContext admin = new com.los.model.UserContext("admin_super", com.los.model.UserRole.ADMIN);

        assertCondition("RBAC: LOAN_OFFICER can create drafts", loanOfficer.hasPermission(com.los.model.Permission.APPLICATION_DRAFT_CREATE));
        assertCondition("RBAC: LOAN_OFFICER cannot review applications", !loanOfficer.hasPermission(com.los.model.Permission.APPLICATION_REVIEW));
        assertCondition("RBAC: CREDIT_OFFICER can review applications", creditOfficer.hasPermission(com.los.model.Permission.APPLICATION_REVIEW));
        assertCondition("RBAC: CREDIT_OFFICER cannot disburse loans", !creditOfficer.hasPermission(com.los.model.Permission.APPLICATION_DISBURSE));
        assertCondition("RBAC: DISBURSEMENT_OFFICER can disburse loans", treasuryOfficer.hasPermission(com.los.model.Permission.APPLICATION_DISBURSE));
        assertCondition("RBAC: AUDITOR has read-only access (cannot create draft)", !auditor.hasPermission(com.los.model.Permission.APPLICATION_DRAFT_CREATE));
        assertCondition("RBAC: AUDITOR can view reports", auditor.hasPermission(com.los.model.Permission.REPORTS_VIEW));
        assertCondition("RBAC: ADMIN has full permissions", admin.hasPermission(com.los.model.Permission.UTILITIES_EXECUTE) && admin.hasPermission(com.los.model.Permission.APPLICATION_DISBURSE));
    }

    private static void testSupabaseConfiguration() {
        com.los.config.AppConfig cfg = com.los.config.AppConfig.getInstance();
        assertCondition("Supabase URL configured", cfg.getSupabaseUrl().contains("ciemfotaaupazeqxxuev"));
        assertCondition("Supabase Publishable Key loaded", cfg.getSupabasePublishableKey().startsWith("sb_publishable_"));
        assertCondition("Supabase Secret Key loaded", cfg.getSupabaseSecretKey() != null && !cfg.getSupabaseSecretKey().isEmpty());
        assertCondition("Supabase JWKS URL configured", cfg.getSupabaseJwksUrl().contains("jwks.json"));
    }

    private static Customer createCustomer(LocalDate dob) {
        Customer c = new Customer();
        c.setCustomerId("CUST-999999");
        c.setName("Test Runner Customer");
        c.setMobile("9999999999");
        c.setDateOfBirth(dob);
        c.setAddress("Test St");
        c.setKycDetails("PAN999");
        return c;
    }

    private static LoanApplication createApplication(ProductType product, BigDecimal amount, int tenure, BigDecimal income, BigDecimal oblig) {
        LoanApplication app = new LoanApplication();
        app.setCustomerId("CUST-999999");
        app.setProduct(product);
        app.setRequestedAmount(amount);
        app.setTenureMonths(tenure);
        app.setMonthlyIncome(income);
        app.setExistingMonthlyObligations(oblig);
        app.setBranchCode("BR001");
        app.setBranchName("Headquarters");
        return app;
    }

    private static void assertCondition(String testName, boolean condition) {
        assertCondition(testName, condition, "");
    }

    private static void assertCondition(String testName, boolean condition, String errorDetail) {
        if (condition) {
            System.out.printf("  [PASS] %s%n", testName);
            passed++;
        } else {
            System.err.printf("  [FAIL] %s - %s%n", testName, errorDetail);
            failed++;
        }
    }
}
