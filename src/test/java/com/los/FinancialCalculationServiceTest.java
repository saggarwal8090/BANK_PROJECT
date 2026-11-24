package com.los;

import com.los.service.FinancialCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FinancialCalculationServiceTest {

    private FinancialCalculationService financialService;

    @BeforeEach
    void setUp() {
        financialService = new FinancialCalculationService();
    }

    @Test
    @DisplayName("Verify standard EMI calculation: P=100000, n=12, rate=12% -> 8884.88")
    void testStandardEmiCalculation() {
        BigDecimal principal = new BigDecimal("100000.00");
        BigDecimal annualRate = new BigDecimal("12.00");
        int tenureMonths = 12;

        BigDecimal emi = financialService.calculateEMI(principal, annualRate, tenureMonths);

        assertEquals(new BigDecimal("8884.88"), emi, "Proposed EMI must match exactly 8884.88");
    }

    @Test
    @DisplayName("Verify FOIR calculation with existing obligations")
    void testFoirCalculation() {
        BigDecimal obligations = new BigDecimal("10000.00");
        BigDecimal emi = new BigDecimal("8884.88");
        BigDecimal income = new BigDecimal("50000.00");

        // (10000 + 8884.88) / 50000 * 100 = 18884.88 / 500 = 37.76976... -> 37.77%
        BigDecimal foir = financialService.calculateFOIR(obligations, emi, income);

        assertEquals(new BigDecimal("37.77"), foir);
    }

    @Test
    @DisplayName("Verify completed age calculation using Period.between")
    void testAgeCalculation() {
        LocalDate dob = LocalDate.of(2000, 11, 24);
        LocalDate businessDate = LocalDate.of(2026, 11, 24);

        int age = financialService.calculateAge(dob, businessDate);

        assertEquals(26, age);
    }
}
