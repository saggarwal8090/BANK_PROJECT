package com.los.dao;

import com.los.model.ApplicationRegisterItem;
import com.los.model.ApplicationStatus;
import com.los.model.CustomerWithoutDisbursementItem;
import com.los.model.DisbursementSummaryItem;
import com.los.model.OverdueReviewItem;
import com.los.model.PipelineSummaryItem;
import com.los.model.ProductType;
import com.los.model.RepeatApplicantItem;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw JDBC DAO for executing all 6 Parameterized Reports.
 */
public class ReportsDAO {

    public List<ApplicationRegisterItem> getApplicationRegister(Connection conn, ProductType product, ApplicationStatus status) throws SQLException {
        String sql = """
            SELECT 
                la.application_number,
                c.name AS customer_name,
                c.mobile AS customer_mobile,
                la.product,
                la.requested_amount,
                la.proposed_emi,
                la.status,
                la.submitted_at,
                la.branch_code,
                la.branch_name,
                la.eligible
            FROM loan_application la
            JOIN customer c ON la.customer_id = c.customer_id
            WHERE (? IS NULL OR la.product = ?)
              AND (? IS NULL OR la.status = ?)
            ORDER BY la.created_at DESC
        """;
        List<ApplicationRegisterItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            String pStr = product != null ? product.name() : null;
            String sStr = status != null ? status.name() : null;

            ps.setString(1, pStr);
            ps.setString(2, pStr);
            ps.setString(3, sStr);
            ps.setString(4, sStr);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ApplicationRegisterItem item = new ApplicationRegisterItem();
                    item.setApplicationNumber(rs.getString("application_number"));
                    item.setCustomerName(rs.getString("customer_name"));
                    item.setCustomerMobile(rs.getString("customer_mobile"));
                    item.setProduct(ProductType.fromString(rs.getString("product")));
                    item.setRequestedAmount(rs.getBigDecimal("requested_amount"));
                    item.setProposedEmi(rs.getBigDecimal("proposed_emi"));
                    item.setStatus(ApplicationStatus.fromString(rs.getString("status")));
                    Timestamp ts = rs.getTimestamp("submitted_at");
                    if (ts != null) {
                        item.setSubmittedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    item.setBranchCode(rs.getString("branch_code"));
                    item.setBranchName(rs.getString("branch_name"));
                    item.setEligible(rs.getBoolean("eligible"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    public List<PipelineSummaryItem> getProductPipelineSummary(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                product,
                status,
                COUNT(*) AS total_applications,
                COALESCE(SUM(requested_amount), 0.00) AS total_requested_amount
            FROM loan_application
            GROUP BY product, status
            ORDER BY product ASC, status ASC
        """;
        List<PipelineSummaryItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new PipelineSummaryItem(
                    rs.getString("product"),
                    rs.getString("status"),
                    rs.getLong("total_applications"),
                    rs.getBigDecimal("total_requested_amount")
                ));
            }
        }
        return list;
    }

    public List<PipelineSummaryItem> getBranchPipelineSummary(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                branch_code,
                branch_name,
                product,
                COUNT(*) AS total_applications,
                COALESCE(SUM(requested_amount), 0.00) AS total_requested_amount
            FROM loan_application
            GROUP BY branch_code, branch_name, product
            ORDER BY branch_code ASC, product ASC
        """;
        List<PipelineSummaryItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new PipelineSummaryItem(
                    rs.getString("branch_code"),
                    rs.getString("branch_name"),
                    rs.getString("product"),
                    rs.getLong("total_applications"),
                    rs.getBigDecimal("total_requested_amount")
                ));
            }
        }
        return list;
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryDaily(Connection conn, OffsetDateTime start, OffsetDateTime end) throws SQLException {
        String sql = """
            SELECT 
                DATE(d.disbursed_at) AS disbursement_date,
                COUNT(d.disbursement_id) AS total_disbursements,
                COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
            FROM disbursement d
            WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
            GROUP BY DATE(d.disbursed_at)
            ORDER BY disbursement_date DESC
        """;
        return executeDisbursementSummary(conn, sql, start, end, "disbursement_date");
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryMonthly(Connection conn, OffsetDateTime start, OffsetDateTime end) throws SQLException {
        String sql = """
            SELECT 
                TO_CHAR(d.disbursed_at, 'YYYY-MM') AS disbursement_month,
                COUNT(d.disbursement_id) AS total_disbursements,
                COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
            FROM disbursement d
            WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
            GROUP BY TO_CHAR(d.disbursed_at, 'YYYY-MM')
            ORDER BY disbursement_month DESC
        """;
        return executeDisbursementSummary(conn, sql, start, end, "disbursement_month");
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryQuarterly(Connection conn, OffsetDateTime start, OffsetDateTime end) throws SQLException {
        String sql = """
            SELECT 
                TO_CHAR(d.disbursed_at, 'YYYY-"Q"Q') AS disbursement_quarter,
                COUNT(d.disbursement_id) AS total_disbursements,
                COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
            FROM disbursement d
            WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
            GROUP BY TO_CHAR(d.disbursed_at, 'YYYY-"Q"Q')
            ORDER BY disbursement_quarter DESC
        """;
        return executeDisbursementSummary(conn, sql, start, end, "disbursement_quarter");
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryBranch(Connection conn, OffsetDateTime start, OffsetDateTime end) throws SQLException {
        String sql = """
            SELECT 
                la.branch_code,
                la.branch_name,
                la.product,
                COUNT(d.disbursement_id) AS total_disbursements,
                COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
            FROM disbursement d
            JOIN loan_application la ON d.application_id = la.application_id
            WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
            GROUP BY la.branch_code, la.branch_name, la.product
            ORDER BY la.branch_code ASC, la.product ASC
        """;
        List<DisbursementSummaryItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            ps.setTimestamp(2, Timestamp.valueOf(end.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String branch = rs.getString("branch_code") + " - " + rs.getString("branch_name");
                    String prod = rs.getString("product");
                    list.add(new DisbursementSummaryItem(
                        branch,
                        prod,
                        rs.getLong("total_disbursements"),
                        rs.getBigDecimal("total_disbursed_amount")
                    ));
                }
            }
        }
        return list;
    }

    private List<DisbursementSummaryItem> executeDisbursementSummary(Connection conn, String sql, OffsetDateTime start, OffsetDateTime end, String colName) throws SQLException {
        List<DisbursementSummaryItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            ps.setTimestamp(2, Timestamp.valueOf(end.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DisbursementSummaryItem(
                        rs.getString(colName),
                        rs.getLong("total_disbursements"),
                        rs.getBigDecimal("total_disbursed_amount")
                    ));
                }
            }
        }
        return list;
    }

    public List<OverdueReviewItem> getOverdueReviews(Connection conn, LocalDate businessDate, int thresholdDays) throws SQLException {
        String sql = """
            SELECT 
                la.application_id,
                la.application_number,
                c.customer_id,
                c.name AS customer_name,
                c.mobile AS customer_mobile,
                la.product,
                la.requested_amount,
                la.submitted_at,
                (?::DATE - la.submitted_at::DATE) AS days_waiting
            FROM loan_application la
            JOIN customer c ON la.customer_id = c.customer_id
            WHERE la.status = 'UNDER_REVIEW'
              AND la.submitted_at IS NOT NULL
              AND (?::DATE - la.submitted_at::DATE) > ?
            ORDER BY days_waiting DESC, la.submitted_at ASC
        """;
        List<OverdueReviewItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(businessDate));
            ps.setDate(2, Date.valueOf(businessDate));
            ps.setInt(3, thresholdDays);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OverdueReviewItem item = new OverdueReviewItem();
                    item.setApplicationId(rs.getLong("application_id"));
                    item.setApplicationNumber(rs.getString("application_number"));
                    item.setCustomerId(rs.getString("customer_id"));
                    item.setCustomerName(rs.getString("customer_name"));
                    item.setCustomerMobile(rs.getString("customer_mobile"));
                    item.setProduct(ProductType.fromString(rs.getString("product")));
                    item.setRequestedAmount(rs.getBigDecimal("requested_amount"));
                    Timestamp ts = rs.getTimestamp("submitted_at");
                    if (ts != null) {
                        item.setSubmittedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    item.setDaysWaiting(rs.getLong("days_waiting"));
                    list.add(item);
                }
            }
        }
        return list;
    }

    public List<CustomerWithoutDisbursementItem> getCustomersWithoutDisbursement(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                c.customer_id,
                c.name AS customer_name,
                c.mobile,
                c.created_at AS registration_date,
                COUNT(la.application_id) AS total_applications
            FROM customer c
            JOIN loan_application la ON c.customer_id = la.customer_id
            LEFT JOIN disbursement d ON la.application_id = d.application_id
            GROUP BY c.customer_id, c.name, c.mobile, c.created_at
            HAVING COUNT(d.disbursement_id) = 0
            ORDER BY c.name ASC
        """;
        List<CustomerWithoutDisbursementItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CustomerWithoutDisbursementItem item = new CustomerWithoutDisbursementItem();
                item.setCustomerId(rs.getString("customer_id"));
                item.setCustomerName(rs.getString("customer_name"));
                item.setMobile(rs.getString("mobile"));
                Timestamp ts = rs.getTimestamp("registration_date");
                if (ts != null) {
                    item.setRegistrationDate(ts.toInstant().atOffset(ZoneOffset.UTC));
                }
                item.setTotalApplications(rs.getLong("total_applications"));
                list.add(item);
            }
        }
        return list;
    }

    public List<RepeatApplicantItem> getRepeatApplicants(Connection conn) throws SQLException {
        String sql = """
            SELECT 
                c.customer_id,
                c.name AS customer_name,
                c.mobile,
                COUNT(la.application_id) AS application_count,
                COALESCE(SUM(la.requested_amount), 0.00) AS total_requested_amount
            FROM customer c
            JOIN loan_application la ON c.customer_id = la.customer_id
            GROUP BY c.customer_id, c.name, c.mobile
            HAVING COUNT(la.application_id) > 1
            ORDER BY application_count DESC, total_requested_amount DESC
        """;
        List<RepeatApplicantItem> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RepeatApplicantItem item = new RepeatApplicantItem();
                item.setCustomerId(rs.getString("customer_id"));
                item.setCustomerName(rs.getString("customer_name"));
                item.setMobile(rs.getString("mobile"));
                item.setApplicationCount(rs.getLong("application_count"));
                item.setTotalRequestedAmount(rs.getBigDecimal("total_requested_amount"));
                list.add(item);
            }
        }
        return list;
    }
}
