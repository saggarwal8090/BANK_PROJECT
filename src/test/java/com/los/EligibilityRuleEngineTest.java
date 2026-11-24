package com.los;

import com.los.model.Customer;
import com.los.model.EligibilityResult;
import com.los.model.LoanApplication;
import com.los.model.ProductType;
import com.los.service.EligibilityRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EligibilityRuleEngineTest {

    private EligibilityRuleEngine ruleEngine;
    private final LocalDate businessDate = LocalDate.of(2026, 11, 24);

    @BeforeEach
    void setUp() {
        ruleEngine = new EligibilityRuleEngine();
    }

    private Customer createCustomer(LocalDate dob) {
        Customer c = new Customer();
        c.setCustomerId("CUST-123456");
        c.setName("Test Applicant");
        c.setMobile("9876543210");
        c.setDateOfBirth(dob);
        c.setAddress("Test Address");
        c.setKycDetails("PAN123");
        return c;
    }

    private LoanApplication createApplication(ProductType product, BigDecimal amount, int tenure, BigDecimal income, BigDecimal oblig) {
        LoanApplication app = new LoanApplication();
        app.setCustomerId("CUST-123456");
        app.setProduct(product);
        app.setRequestedAmount(amount);
        app.setTenureMonths(tenure);
        app.setMonthlyIncome(income);
        app.setExistingMonthlyObligations(oblig);
        app.setBranchCode("BR001");
        app.setBranchName("Main");
        return app;
    }

    @Test
    @DisplayName("Age boundary: exactly 21 years -> Eligible")
    void testAge21Eligible() {
        LocalDate dob = businessDate.minusYears(21);
        Customer c = createCustomer(dob);
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);

        EligibilityResult res = ruleEngine.evaluate(c, app, businessDate);
        assertTrue(res.isEligible(), "Age 21 should be eligible");
    }

    @Test
    @DisplayName("Age boundary: 20 years and 364 days -> Ineligible")
    void testAge20Ineligible() {
        LocalDate dob = businessDate.minusYears(21).plusDays(1);
        Customer c = createCustomer(dob);
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);

        EligibilityResult res = ruleEngine.evaluate(c, app, businessDate);
        assertFalse(res.isEligible(), "Age < 21 must be rejected");
    }

    @Test
    @DisplayName("Age boundary: exactly 60 years -> Eligible")
    void testAge60Eligible() {
        LocalDate dob = businessDate.minusYears(60);
        Customer c = createCustomer(dob);
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);

        EligibilityResult res = ruleEngine.evaluate(c, app, businessDate);
        assertTrue(res.isEligible(), "Age 60 should be eligible");
    }

    @Test
    @DisplayName("Age boundary: 61 years -> Ineligible")
    void testAge61Ineligible() {
        LocalDate dob = businessDate.minusYears(61);
        Customer c = createCustomer(dob);
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);

        EligibilityResult res = ruleEngine.evaluate(c, app, businessDate);
        assertFalse(res.isEligible(), "Age > 60 must be rejected");
    }

    @Test
    @DisplayName("Future DOB -> Rejected")
    void testFutureDobRejected() {
        LocalDate dob = businessDate.plusDays(1);
        Customer c = createCustomer(dob);
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);

        EligibilityResult res = ruleEngine.evaluate(c, app, businessDate);
        assertFalse(res.isEligible(), "Future DOB must be rejected");
    }

    @Test
    @DisplayName("Personal Loan: Amount bounds (50,000 to 500,000)")
    void testPersonalAmountBounds() {
        Customer c = createCustomer(businessDate.minusYears(30));

        // 49,999 -> Fail
        LoanApplication appLow = createApplication(ProductType.PERSONAL, new BigDecimal("49999.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertFalse(ruleEngine.evaluate(c, appLow, businessDate).isEligible());

        // 50,000 -> Pass
        LoanApplication appMin = createApplication(ProductType.PERSONAL, new BigDecimal("50000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertTrue(ruleEngine.evaluate(c, appMin, businessDate).isEligible());

        // 500,000 -> Pass
        LoanApplication appMax = createApplication(ProductType.PERSONAL, new BigDecimal("500000.00"), 60, new BigDecimal("100000.00"), BigDecimal.ZERO);
        assertTrue(ruleEngine.evaluate(c, appMax, businessDate).isEligible());

        // 500,001 -> Fail
        LoanApplication appHigh = createApplication(ProductType.PERSONAL, new BigDecimal("500001.00"), 60, new BigDecimal("100000.00"), BigDecimal.ZERO);
        assertFalse(ruleEngine.evaluate(c, appHigh, businessDate).isEligible());
    }

    @Test
    @DisplayName("Education Loan: Amount bounds (100,000 to 1,000,000) & Tenure (12 to 84m)")
    void testEducationBounds() {
        Customer c = createCustomer(businessDate.minusYears(25));

        // 99,999 -> Fail
        LoanApplication appLow = createApplication(ProductType.EDUCATION, new BigDecimal("99999.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertFalse(ruleEngine.evaluate(c, appLow, businessDate).isEligible());

        // 100,000 -> Pass
        LoanApplication appMin = createApplication(ProductType.EDUCATION, new BigDecimal("100000.00"), 12, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertTrue(ruleEngine.evaluate(c, appMin, businessDate).isEligible());

        // 1,000,000 with 84m tenure -> Pass
        LoanApplication appMax = createApplication(ProductType.EDUCATION, new BigDecimal("1000000.00"), 84, new BigDecimal("80000.00"), BigDecimal.ZERO);
        assertTrue(ruleEngine.evaluate(c, appMax, businessDate).isEligible());

        // 85m tenure -> Fail
        LoanApplication appTenureFail = createApplication(ProductType.EDUCATION, new BigDecimal("500000.00"), 85, new BigDecimal("50000.00"), BigDecimal.ZERO);
        assertFalse(ruleEngine.evaluate(c, appTenureFail, businessDate).isEligible());
    }

    @Test
    @DisplayName("FOIR Boundary Check: <= 50.00% is Eligible, > 50.00% is Ineligible")
    void testFoirBoundary() {
        Customer c = createCustomer(businessDate.minusYears(30));

        // Income = 20,000. Personal loan 50,000, 12m, 12%. EMI = 4,442.44
        // If obligations = 5,557.56 -> Total = 10,000.00 -> FOIR = exactly 50.00% -> Pass
        LoanApplication appPass = createApplication(ProductType.PERSONAL, new BigDecimal("50000.00"), 12, new BigDecimal("20000.00"), new BigDecimal("5557.56"));
        EligibilityResult resPass = ruleEngine.evaluate(c, appPass, businessDate);
        assertTrue(resPass.isEligible(), "FOIR exactly 50.00% should pass");

        // If obligations = 5,558.00 -> Total = 10,000.44 -> FOIR = 50.0022% -> 50.00% half-up? Let's give obligations = 6,000.00 -> FOIR = 52.21% -> Fail
        LoanApplication appFail = createApplication(ProductType.PERSONAL, new BigDecimal("50000.00"), 12, new BigDecimal("20000.00"), new BigDecimal("6000.00"));
        EligibilityResult resFail = ruleEngine.evaluate(c, appFail, businessDate);
        assertFalse(resFail.isEligible(), "FOIR > 50.00% must be rejected");
    }

    @Test
    @DisplayName("Non-short-circuiting: Collects all multiple rule failures")
    void testCollectsMultipleFailures() {
        // Customer under 21, negative amount, low income, excessive obligations
        Customer c = createCustomer(businessDate.minusYears(19)); // Failure 1
        LoanApplication app = createApplication(ProductType.PERSONAL, new BigDecimal("10000.00"), 6, new BigDecimal("5000.00"), new BigDecimal("10000.00"));
        // Failure 2: Amount < 50k
        // Failure 3: Tenure < 12m
        // Failure 4: Income < 20k
        // Failure 5: FOIR > 50%

        EligibilityResult res = ruleEngine.evaluate(c, app, businessDate);
        assertFalse(res.isEligible());
        assertTrue(res.getReasons().size() >= 4, "Must collect multiple failure reasons without early short-circuiting");
    }
}
