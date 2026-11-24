package com.los.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of Underwriting & Eligibility Evaluation.
 */
public class EligibilityResult {

    private final boolean eligible;
    private final BigDecimal proposedEmi;
    private final BigDecimal foirPercentage;
    private final List<String> reasons;

    public EligibilityResult(boolean eligible, BigDecimal proposedEmi, BigDecimal foirPercentage, List<String> reasons) {
        this.eligible = eligible;
        this.proposedEmi = proposedEmi;
        this.foirPercentage = foirPercentage;
        this.reasons = reasons != null ? new ArrayList<>(reasons) : new ArrayList<>();
    }

    public boolean isEligible() {
        return eligible;
    }

    public BigDecimal getProposedEmi() {
        return proposedEmi;
    }

    public BigDecimal getFoirPercentage() {
        return foirPercentage;
    }

    public List<String> getReasons() {
        return new ArrayList<>(reasons);
    }

    public String getFormattedReasons() {
        if (reasons.isEmpty()) {
            return "Eligible - All underwriting checks passed";
        }
        return String.join(" | ", reasons);
    }

    @Override
    public String toString() {
        return "EligibilityResult{" +
                "eligible=" + eligible +
                ", proposedEmi=" + proposedEmi +
                ", foirPercentage=" + foirPercentage +
                ", reasons=" + reasons +
                '}';
    }
}
