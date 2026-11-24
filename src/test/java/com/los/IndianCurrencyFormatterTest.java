package com.los;

import com.los.config.IndianCurrencyFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndianCurrencyFormatterTest {

    @Test
    @DisplayName("Verify Indian numbering pattern ##,##,###,###.##")
    void testIndianNumberFormatting() {
        assertEquals("10,00,000.00", IndianCurrencyFormatter.format(new BigDecimal("1000000.00")));
        assertEquals("1,00,000.00", IndianCurrencyFormatter.format(new BigDecimal("100000.00")));
        assertEquals("50,000.00", IndianCurrencyFormatter.format(new BigDecimal("50000.00")));
        assertEquals("8,884.88", IndianCurrencyFormatter.format(new BigDecimal("8884.88")));
        assertEquals("500.00", IndianCurrencyFormatter.format(new BigDecimal("500.00")));
        assertEquals("0.00", IndianCurrencyFormatter.format(BigDecimal.ZERO));
        assertEquals("-10,00,000.00", IndianCurrencyFormatter.format(new BigDecimal("-1000000.00")));
        assertEquals("1,23,45,678.90", IndianCurrencyFormatter.format(new BigDecimal("12345678.90")));
    }
}
