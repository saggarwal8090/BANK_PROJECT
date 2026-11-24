package com.los.config;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for formatting currency values strictly adhering to the Indian Numbering System:
 * e.g., 10,00,000.00 (Crores, Lakhs, Thousands, Hundreds).
 */
public class IndianCurrencyFormatter {

    private IndianCurrencyFormatter() {
        // Utility class
    }

    /**
     * Formats a BigDecimal amount as an Indian currency string with 2 decimal places.
     * Example: 1000000.00 -> "10,00,000.00"
     *
     * @param amount the numeric amount
     * @return formatted INR string with commas according to Indian numbering
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }

        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        boolean isNegative = scaled.signum() < 0;
        String absStr = scaled.abs().toPlainString();

        String[] parts = absStr.split("\\.");
        String integerPart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "00";

        StringBuilder formattedInt = new StringBuilder();
        int len = integerPart.length();

        if (len <= 3) {
            formattedInt.append(integerPart);
        } else {
            // Last 3 digits (hundreds group)
            String lastThree = integerPart.substring(len - 3);
            String remaining = integerPart.substring(0, len - 3);

            StringBuilder remFormatted = new StringBuilder();
            int remLen = remaining.length();

            // Group remaining in pairs of 2 from right to left
            int count = 0;
            for (int i = remLen - 1; i >= 0; i--) {
                remFormatted.insert(0, remaining.charAt(i));
                count++;
                if (count % 2 == 0 && i > 0) {
                    remFormatted.insert(0, ",");
                }
            }

            formattedInt.append(remFormatted).append(",").append(lastThree);
        }

        String result = formattedInt + "." + decimalPart;
        return isNegative ? "-" + result : result;
    }

    /**
     * Formats an amount with the INR currency symbol or prefix.
     * Example: "INR 10,00,000.00"
     */
    public static String formatWithPrefix(BigDecimal amount) {
        return "INR " + format(amount);
    }
}
