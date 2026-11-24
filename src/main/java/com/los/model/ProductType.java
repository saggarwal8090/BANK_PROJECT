package com.los.model;

/**
 * Supported Loan Product Types.
 */
public enum ProductType {
    PERSONAL,
    EDUCATION;

    public static ProductType fromString(String val) {
        if (val == null) return null;
        for (ProductType pt : values()) {
            if (pt.name().equalsIgnoreCase(val.trim())) {
                return pt;
            }
        }
        throw new IllegalArgumentException("Unknown product type: " + val);
    }
}
