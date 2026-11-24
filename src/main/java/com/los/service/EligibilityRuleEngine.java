package com.los.service;

import com.los.config.AppConfig;
import com.los.config.IndianCurrencyFormatter;
import com.los.model.Customer;
import com.los.model.EligibilityResult;
import com.los.model.LoanApplication;
import com.los.model.ProductType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Underwriting & Eligibility Evaluation Engine.
 *
 * Strict Business Rules:
 *  - Evaluates ALL rules comprehensively without short-circuiting on the first failure.
 *  - Stores and displays every failed rule reason.
 *  - Validates age (21 to 60 completed years as of Business Date, rejects future DOB).
 *  - Product bounds:
 *      * PERSONAL: Amount 50k-500k, Tenure 12-60m, Min Income 20k, Rate 12% p.a.
 *      * EDUCATION: Amount 100k-1M, Tenure 12-84m, Min Income 15k, Rate 10% p.a.
 *  - FOIR threshold <= 50.00%.
 */
public class EligibilityRuleEngine {

    private final AppConfig config;
    private final FinancialCalculationService financialService;

    public EligibilityRuleEngine() {
        this.config = AppConfig.getInstance();
        this.financialService = new FinancialCalculationService();
    }

    public EligibilityRuleEngine(AppConfig config, FinancialCalculationService financialService) {
        this.config = config;
        this.financialService = financialService;
    }

    /**
     * Evaluates a loan application against all product, financial, and regulatory policies.
     *
     * @param customer applicant customer details
     * @param app loan application details
     * @param businessDate evaluation reference business date
     * @return EligibilityResult containing pass/fail flag, computed EMI, computed FOIR, and list of violations
     */
    public EligibilityResult evaluate(Customer customer, LoanApplication app, LocalDate businessDate) {
        List<String> reasons = new ArrayList<>();

        if (businessDate == null) {
            businessDate = config.getBusinessDate();
        }

        // 1. Customer DOB & Age Checks
        if (customer == null || customer.getDateOfBirth() == null) {
            reasons.add("Customer date of birth is missing or invalid.");
        } else {
            LocalDate dob = customer.getDateOfBirth();
            if (dob.isAfter(businessDate)) {
                reasons.add(String.format("Customer date of birth [%s] cannot be in the future relative to business date [%s].",
                        dob, businessDate));
            } else {
                int age = financialService.calculateAge(dob, businessDate);
                int minAge = config.getMinAge();
                int maxAge = config.getMaxAge();
                if (age < minAge || age > maxAge) {
                    reasons.add(String.format("Customer age [%d years] is outside eligible policy range [%d to %d years].",
                            age, minAge, maxAge));
                }
            }
        }

        // 2. Product-Specific Rules
        ProductType product = app.getProduct();
        if (product == null) {
            reasons.add("Loan product type must be specified (PERSONAL or EDUCATION).");
            return new EligibilityResult(false, BigDecimal.ZERO, BigDecimal.ZERO, reasons);
        }

        BigDecimal minAmount;
        BigDecimal maxAmount;
        int minTenure;
        int maxTenure;
        BigDecimal minIncome;
        BigDecimal annualRate;

        if (product == ProductType.PERSONAL) {
            minAmount = config.getPersonalMinAmount();
            maxAmount = config.getPersonalMaxAmount();
            minTenure = config.getPersonalMinTenure();
            maxTenure = config.getPersonalMaxTenure();
            minIncome = config.getPersonalMinIncome();
            annualRate = config.getPersonalInterestRate();
        } else {
            minAmount = config.getEducationMinAmount();
            maxAmount = config.getEducationMaxAmount();
            minTenure = config.getEducationMinTenure();
            maxTenure = config.getEducationMaxTenure();
            minIncome = config.getEducationMinIncome();
            annualRate = config.getEducationInterestRate();
        }

        // Ensure application reflects configured interest rate for the product
        app.setAnnualInterestRate(annualRate);

        // 3. Requested Amount Boundary Validation
        BigDecimal reqAmount = app.getRequestedAmount();
        if (reqAmount == null || reqAmount.signum() <= 0) {
            reasons.add("Requested loan amount must be strictly greater than zero.");
        } else if (reqAmount.compareTo(minAmount) < 0 || reqAmount.compareTo(maxAmount) > 0) {
            reasons.add(String.format("Requested amount [%s] is outside allowed product range [%s to %s].",
                    IndianCurrencyFormatter.formatWithPrefix(reqAmount),
                    IndianCurrencyFormatter.formatWithPrefix(minAmount),
                    IndianCurrencyFormatter.formatWithPrefix(maxAmount)));
        }

        // 4. Tenure Months Validation
        int tenure = app.getTenureMonths();
        if (tenure <= 0) {
            reasons.add("Loan tenure must be a positive whole number of months.");
        } else if (tenure < minTenure || tenure > maxTenure) {
            reasons.add(String.format("Requested tenure [%d months] is outside allowed range [%d to %d months] for %s.",
                    tenure, minTenure, maxTenure, product.name()));
        }

        // 5. Monthly Net Income Validation
        BigDecimal income = app.getMonthlyIncome();
        if (income == null || income.signum() <= 0) {
            reasons.add("Monthly net income must be strictly greater than zero.");
        } else if (income.compareTo(minIncome) < 0) {
            reasons.add(String.format("Monthly income [%s] is below minimum required income [%s] for %s loan.",
                    IndianCurrencyFormatter.formatWithPrefix(income),
                    IndianCurrencyFormatter.formatWithPrefix(minIncome),
                    product.name()));
        }

        // 6. Existing Monthly Obligations Validation
        BigDecimal obligations = app.getExistingMonthlyObligations();
        if (obligations == null || obligations.signum() < 0) {
            reasons.add("Existing monthly obligations cannot be negative.");
            obligations = BigDecimal.ZERO;
        }

        // 7. Proposed EMI Calculation
        BigDecimal proposedEmi = BigDecimal.ZERO;
        if (reqAmount != null && reqAmount.signum() > 0 && tenure > 0) {
            proposedEmi = financialService.calculateEMI(reqAmount, annualRate, tenure);
        }
        app.setProposedEmi(proposedEmi);

        // 8. Fixed Obligation to Income Ratio (FOIR) Validation
        BigDecimal foir = BigDecimal.ZERO;
        if (income != null && income.signum() > 0) {
            foir = financialService.calculateFOIR(obligations, proposedEmi, income);
        } else {
            foir = new BigDecimal("100.00");
        }
        app.setFoirPercentage(foir);

        BigDecimal maxFoir = config.getMaxFoirPercentage();
        if (foir.compareTo(maxFoir) > 0) {
            reasons.add(String.format("Computed FOIR [%s%%] exceeds maximum permissible policy threshold of [%s%%].",
                    foir.toPlainString(), maxFoir.toPlainString()));
        }

        boolean eligible = reasons.isEmpty();
        app.setEligible(eligible);
        app.setEligibilityReasons(eligible ? "All underwriting criteria passed successfully." : String.join(" | ", reasons));

        return new EligibilityResult(eligible, proposedEmi, foir, reasons);
    }
}
