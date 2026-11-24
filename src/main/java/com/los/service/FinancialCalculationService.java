package com.los.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Enterprise Financial Calculation Service.
 * Implements standard Equated Monthly Installment (EMI) and
 * Fixed Obligation to Income Ratio (FOIR) calculations using pure BigDecimal (Scale 2, HALF_UP).
 */
public class FinancialCalculationService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Calculates Equated Monthly Installment (EMI):
     * Formula: EMI = [P * r * (1 + r)^n] / [(1 + r)^n - 1]
     * where:
     *   P = Principal requested amount
     *   r = Monthly interest rate = (Annual Interest Rate / 12 / 100)
     *   n = Loan tenure in months
     *
     * Result is rounded to 2 decimal places using RoundingMode.HALF_UP.
     * Example: P = 100,000, n = 12, rate = 12% -> Proposed EMI = 8,884.88
     *
     * @param principal requested amount (P)
     * @param annualInterestRate annual interest rate as percentage (e.g. 12.00)
     * @param tenureMonths tenure in months (n)
     * @return monthly EMI rounded to scale 2 HALF_UP
     */
    public BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualInterestRate, int tenureMonths) {
        if (principal == null || annualInterestRate == null || tenureMonths <= 0 || principal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (annualInterestRate.signum() == 0) {
            // 0% interest fallback
            return principal.divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        // r = annualInterestRate / 1200
        BigDecimal r = annualInterestRate.divide(TWELVE_HUNDRED, MC);

        // (1 + r)
        BigDecimal onePlusR = BigDecimal.ONE.add(r, MC);

        // (1 + r)^n
        BigDecimal onePlusRPowerN = onePlusR.pow(tenureMonths, MC);

        // Numerator = P * r * (1 + r)^n
        BigDecimal numerator = principal.multiply(r, MC).multiply(onePlusRPowerN, MC);

        // Denominator = (1 + r)^n - 1
        BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE, MC);

        // EMI = Numerator / Denominator
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates Fixed Obligation to Income Ratio (FOIR):
     * Formula: FOIR (%) = [(existing_monthly_obligations + proposed_EMI) / monthly_net_income] * 100
     *
     * @param existingObligations current monthly obligations
     * @param proposedEmi proposed EMI for requested loan
     * @param monthlyIncome monthly net income
     * @return FOIR percentage rounded to scale 2 HALF_UP
     */
    public BigDecimal calculateFOIR(BigDecimal existingObligations, BigDecimal proposedEmi, BigDecimal monthlyIncome) {
        if (monthlyIncome == null || monthlyIncome.signum() <= 0) {
            return new BigDecimal("100.00");
        }

        BigDecimal obligations = existingObligations != null ? existingObligations : BigDecimal.ZERO;
        BigDecimal emi = proposedEmi != null ? proposedEmi : BigDecimal.ZERO;

        BigDecimal totalObligations = obligations.add(emi, MC);
        return totalObligations.divide(monthlyIncome, MC).multiply(ONE_HUNDRED, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates completed years of age as of a specific business date using Period.between.
     */
    public int calculateAge(LocalDate dateOfBirth, LocalDate businessDate) {
        if (dateOfBirth == null || businessDate == null) {
            return 0;
        }
        return Period.between(dateOfBirth, businessDate).getYears();
    }
}
