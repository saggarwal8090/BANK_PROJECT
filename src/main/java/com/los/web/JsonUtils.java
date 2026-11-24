package com.los.web;

import com.los.config.IndianCurrencyFormatter;
import com.los.model.ApplicationHistory;
import com.los.model.ApplicationRegisterItem;
import com.los.model.Customer;
import com.los.model.CustomerWithoutDisbursementItem;
import com.los.model.Disbursement;
import com.los.model.DisbursementSummaryItem;
import com.los.model.LoanApplication;
import com.los.model.OverdueReviewItem;
import com.los.model.PipelineSummaryItem;
import com.los.model.RepeatApplicantItem;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight JSON Serialization Utility for JDK HTTP Server without external dependencies.
 */
public class JsonUtils {

    private JsonUtils() {
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String toJson(com.los.model.AppUser u) {
        if (u == null) return "null";
        return String.format(
            "{\"userId\":%d,\"username\":\"%s\",\"fullName\":\"%s\",\"role\":\"%s\",\"email\":\"%s\",\"branchCode\":\"%s\"}",
            u.getUserId() != null ? u.getUserId() : 0,
            escape(u.getUsername()),
            escape(u.getFullName()),
            u.getRole() != null ? u.getRole().name() : "",
            escape(u.getEmail()),
            escape(u.getBranchCode())
        );
    }

    public static String userListToJson(List<com.los.model.AppUser> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String toJson(Customer c) {
        if (c == null) return "null";
        return String.format(
            "{\"customerId\":\"%s\",\"name\":\"%s\",\"dateOfBirth\":\"%s\",\"mobile\":\"%s\",\"address\":\"%s\",\"kycDetails\":\"%s\",\"createdAt\":\"%s\"}",
            escape(c.getCustomerId()),
            escape(c.getName()),
            c.getDateOfBirth() != null ? c.getDateOfBirth().toString() : "",
            escape(c.getMobile()),
            escape(c.getAddress()),
            escape(c.getKycDetails()),
            c.getCreatedAt() != null ? c.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : ""
        );
    }

    public static String toJson(LoanApplication app) {
        if (app == null) return "null";
        return String.format(
            "{\"applicationId\":%d,\"applicationNumber\":\"%s\",\"customerId\":\"%s\",\"product\":\"%s\",\"branchCode\":\"%s\",\"branchName\":\"%s\",\"requestedAmount\":%s,\"formattedRequestedAmount\":\"%s\",\"tenureMonths\":%d,\"monthlyIncome\":%s,\"existingMonthlyObligations\":%s,\"annualInterestRate\":%s,\"proposedEmi\":%s,\"formattedProposedEmi\":\"%s\",\"foirPercentage\":%s,\"eligible\":%b,\"eligibilityReasons\":\"%s\",\"status\":\"%s\",\"createdAt\":\"%s\",\"submittedAt\":\"%s\",\"decidedAt\":\"%s\",\"reviewerCode\":\"%s\",\"decisionReason\":\"%s\"}",
            app.getApplicationId() != null ? app.getApplicationId() : 0,
            escape(app.getApplicationNumber()),
            escape(app.getCustomerId()),
            app.getProduct() != null ? app.getProduct().name() : "",
            escape(app.getBranchCode()),
            escape(app.getBranchName()),
            app.getRequestedAmount() != null ? app.getRequestedAmount().toPlainString() : "0.00",
            IndianCurrencyFormatter.formatWithPrefix(app.getRequestedAmount()),
            app.getTenureMonths(),
            app.getMonthlyIncome() != null ? app.getMonthlyIncome().toPlainString() : "0.00",
            app.getExistingMonthlyObligations() != null ? app.getExistingMonthlyObligations().toPlainString() : "0.00",
            app.getAnnualInterestRate() != null ? app.getAnnualInterestRate().toPlainString() : "0.00",
            app.getProposedEmi() != null ? app.getProposedEmi().toPlainString() : "0.00",
            IndianCurrencyFormatter.formatWithPrefix(app.getProposedEmi()),
            app.getFoirPercentage() != null ? app.getFoirPercentage().toPlainString() : "0.00",
            app.isEligible(),
            escape(app.getEligibilityReasons()),
            app.getStatus() != null ? app.getStatus().name() : "",
            app.getCreatedAt() != null ? app.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
            app.getSubmittedAt() != null ? app.getSubmittedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
            app.getDecidedAt() != null ? app.getDecidedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
            escape(app.getReviewerCode()),
            escape(app.getDecisionReason())
        );
    }

    public static String toJson(Disbursement d) {
        if (d == null) return "null";
        return String.format(
            "{\"disbursementId\":%d,\"applicationId\":%d,\"reference\":\"%s\",\"amount\":%s,\"formattedAmount\":\"%s\",\"disbursedAt\":\"%s\"}",
            d.getDisbursementId() != null ? d.getDisbursementId() : 0,
            d.getApplicationId() != null ? d.getApplicationId() : 0,
            escape(d.getReference()),
            d.getAmount() != null ? d.getAmount().toPlainString() : "0.00",
            IndianCurrencyFormatter.formatWithPrefix(d.getAmount()),
            d.getDisbursedAt() != null ? d.getDisbursedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : ""
        );
    }

    public static String toJson(ApplicationHistory h) {
        if (h == null) return "null";
        return String.format(
            "{\"historyId\":%d,\"applicationId\":%d,\"action\":\"%s\",\"oldStatus\":\"%s\",\"newStatus\":\"%s\",\"performedBy\":\"%s\",\"performedAt\":\"%s\",\"remarks\":\"%s\"}",
            h.getHistoryId() != null ? h.getHistoryId() : 0,
            h.getApplicationId() != null ? h.getApplicationId() : 0,
            escape(h.getAction()),
            h.getOldStatus() != null ? h.getOldStatus().name() : "",
            h.getNewStatus() != null ? h.getNewStatus().name() : "",
            escape(h.getPerformedBy()),
            h.getPerformedAt() != null ? h.getPerformedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
            escape(h.getRemarks())
        );
    }

    public static String customerListToJson(List<Customer> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String applicationListToJson(List<LoanApplication> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String historyListToJson(List<ApplicationHistory> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append(toJson(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String registerReportToJson(List<ApplicationRegisterItem> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            ApplicationRegisterItem item = list.get(i);
            sb.append(String.format(
                "{\"applicationNumber\":\"%s\",\"customerName\":\"%s\",\"customerMobile\":\"%s\",\"product\":\"%s\",\"requestedAmount\":%s,\"formattedRequestedAmount\":\"%s\",\"proposedEmi\":%s,\"formattedProposedEmi\":\"%s\",\"status\":\"%s\",\"submittedAt\":\"%s\",\"branchCode\":\"%s\",\"branchName\":\"%s\",\"eligible\":%b}",
                escape(item.getApplicationNumber()),
                escape(item.getCustomerName()),
                escape(item.getCustomerMobile()),
                item.getProduct() != null ? item.getProduct().name() : "",
                item.getRequestedAmount() != null ? item.getRequestedAmount().toPlainString() : "0.00",
                IndianCurrencyFormatter.formatWithPrefix(item.getRequestedAmount()),
                item.getProposedEmi() != null ? item.getProposedEmi().toPlainString() : "0.00",
                IndianCurrencyFormatter.formatWithPrefix(item.getProposedEmi()),
                item.getStatus() != null ? item.getStatus().name() : "",
                item.getSubmittedAt() != null ? item.getSubmittedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
                escape(item.getBranchCode()),
                escape(item.getBranchName()),
                item.isEligible()
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String pipelineReportToJson(List<PipelineSummaryItem> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            PipelineSummaryItem item = list.get(i);
            sb.append(String.format(
                "{\"key1\":\"%s\",\"key2\":\"%s\",\"key3\":\"%s\",\"totalApplications\":%d,\"totalRequestedAmount\":%s,\"formattedAmount\":\"%s\"}",
                escape(item.getGroupKey1()),
                escape(item.getGroupKey2()),
                escape(item.getGroupKey3()),
                item.getTotalApplications(),
                item.getTotalRequestedAmount() != null ? item.getTotalRequestedAmount().toPlainString() : "0.00",
                IndianCurrencyFormatter.formatWithPrefix(item.getTotalRequestedAmount())
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String disbursementReportToJson(List<DisbursementSummaryItem> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            DisbursementSummaryItem item = list.get(i);
            sb.append(String.format(
                "{\"periodOrBranch\":\"%s\",\"secondaryInfo\":\"%s\",\"totalDisbursements\":%d,\"totalDisbursedAmount\":%s,\"formattedAmount\":\"%s\"}",
                escape(item.getPeriodOrBranch()),
                escape(item.getSecondaryInfo()),
                item.getTotalDisbursements(),
                item.getTotalDisbursedAmount() != null ? item.getTotalDisbursedAmount().toPlainString() : "0.00",
                IndianCurrencyFormatter.formatWithPrefix(item.getTotalDisbursedAmount())
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String overdueReportToJson(List<OverdueReviewItem> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            OverdueReviewItem item = list.get(i);
            sb.append(String.format(
                "{\"applicationId\":%d,\"applicationNumber\":\"%s\",\"customerId\":\"%s\",\"customerName\":\"%s\",\"customerMobile\":\"%s\",\"product\":\"%s\",\"requestedAmount\":%s,\"formattedAmount\":\"%s\",\"submittedAt\":\"%s\",\"daysWaiting\":%d}",
                item.getApplicationId() != null ? item.getApplicationId() : 0,
                escape(item.getApplicationNumber()),
                escape(item.getCustomerId()),
                escape(item.getCustomerName()),
                escape(item.getCustomerMobile()),
                item.getProduct() != null ? item.getProduct().name() : "",
                item.getRequestedAmount() != null ? item.getRequestedAmount().toPlainString() : "0.00",
                IndianCurrencyFormatter.formatWithPrefix(item.getRequestedAmount()),
                item.getSubmittedAt() != null ? item.getSubmittedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
                item.getDaysWaiting()
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String noDisbursementReportToJson(List<CustomerWithoutDisbursementItem> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            CustomerWithoutDisbursementItem item = list.get(i);
            sb.append(String.format(
                "{\"customerId\":\"%s\",\"customerName\":\"%s\",\"mobile\":\"%s\",\"registrationDate\":\"%s\",\"totalApplications\":%d}",
                escape(item.getCustomerId()),
                escape(item.getCustomerName()),
                escape(item.getMobile()),
                item.getRegistrationDate() != null ? item.getRegistrationDate().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
                item.getTotalApplications()
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String repeatApplicantsReportToJson(List<RepeatApplicantItem> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            RepeatApplicantItem item = list.get(i);
            sb.append(String.format(
                "{\"customerId\":\"%s\",\"customerName\":\"%s\",\"mobile\":\"%s\",\"applicationCount\":%d,\"totalRequestedAmount\":%s,\"formattedAmount\":\"%s\"}",
                escape(item.getCustomerId()),
                escape(item.getCustomerName()),
                escape(item.getMobile()),
                item.getApplicationCount(),
                item.getTotalRequestedAmount() != null ? item.getTotalRequestedAmount().toPlainString() : "0.00",
                IndianCurrencyFormatter.formatWithPrefix(item.getTotalRequestedAmount())
            ));
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Minimalist JSON string parser to convert a JSON object string into a Map of key-value pairs.
     */
    public static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) return map;

        // Split key-value pairs by comma taking quotes into consideration
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        List<String> pairs = new java.util.ArrayList<>();

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (c == ',' && !inQuotes) {
                pairs.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            pairs.add(current.toString().trim());
        }

        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx > 0) {
                String key = unquote(pair.substring(0, colonIdx).trim());
                String val = unquote(pair.substring(colonIdx + 1).trim());
                map.put(key, val);
            }
        }
        return map;
    }

    private static String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
