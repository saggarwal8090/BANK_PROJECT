package com.los.dao;

import com.los.model.ApplicationStatus;
import com.los.model.LoanApplication;
import com.los.model.OverdueReviewItem;
import com.los.model.ProductType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raw JDBC DAO for LoanApplication management.
 */
public class LoanApplicationDAO {

    public Long insert(Connection conn, LoanApplication app) throws SQLException {
        String sql = """
            INSERT INTO loan_application (
                application_number, customer_id, product, branch_code, branch_name,
                requested_amount, tenure_months, monthly_income, existing_monthly_obligations,
                annual_interest_rate, proposed_emi, foir_percentage, eligible, eligibility_reasons,
                status, created_at, submitted_at, decided_at, reviewer_code, decision_reason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, app.getApplicationNumber());
            ps.setString(2, app.getCustomerId());
            ps.setString(3, app.getProduct().name());
            ps.setString(4, app.getBranchCode());
            ps.setString(5, app.getBranchName());
            ps.setBigDecimal(6, app.getRequestedAmount());
            ps.setInt(7, app.getTenureMonths());
            ps.setBigDecimal(8, app.getMonthlyIncome());
            ps.setBigDecimal(9, app.getExistingMonthlyObligations());
            ps.setBigDecimal(10, app.getAnnualInterestRate());
            ps.setBigDecimal(11, app.getProposedEmi());
            ps.setBigDecimal(12, app.getFoirPercentage());
            ps.setBoolean(13, app.isEligible());
            ps.setString(14, app.getEligibilityReasons());
            ps.setString(15, app.getStatus().name());

            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime createdAt = app.getCreatedAt() != null ? app.getCreatedAt() : now;
            ps.setTimestamp(16, Timestamp.valueOf(createdAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));

            setNullableTimestamp(ps, 17, app.getSubmittedAt());
            setNullableTimestamp(ps, 18, app.getDecidedAt());
            ps.setString(19, app.getReviewerCode());
            ps.setString(20, app.getDecisionReason());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    app.setApplicationId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated application_id.");
    }

    public void updateDraft(Connection conn, LoanApplication app) throws SQLException {
        String sql = """
            UPDATE loan_application SET
                product = ?,
                branch_code = ?,
                branch_name = ?,
                requested_amount = ?,
                tenure_months = ?,
                monthly_income = ?,
                existing_monthly_obligations = ?,
                annual_interest_rate = ?,
                proposed_emi = ?,
                foir_percentage = ?
            WHERE application_id = ? AND status = 'DRAFT'
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, app.getProduct().name());
            ps.setString(2, app.getBranchCode());
            ps.setString(3, app.getBranchName());
            ps.setBigDecimal(4, app.getRequestedAmount());
            ps.setInt(5, app.getTenureMonths());
            ps.setBigDecimal(6, app.getMonthlyIncome());
            ps.setBigDecimal(7, app.getExistingMonthlyObligations());
            ps.setBigDecimal(8, app.getAnnualInterestRate());
            ps.setBigDecimal(9, app.getProposedEmi());
            ps.setBigDecimal(10, app.getFoirPercentage());
            ps.setLong(11, app.getApplicationId());

            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Application draft update failed. Application not found or not in DRAFT status.");
            }
        }
    }

    public void updateSubmissionStatus(Connection conn, Long applicationId, ApplicationStatus status, boolean eligible, String reasons, OffsetDateTime submittedAt, OffsetDateTime decidedAt, String decisionReason) throws SQLException {
        String sql = """
            UPDATE loan_application SET
                status = ?,
                eligible = ?,
                eligibility_reasons = ?,
                submitted_at = ?,
                decided_at = ?,
                decision_reason = ?
            WHERE application_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setBoolean(2, eligible);
            ps.setString(3, reasons);
            setNullableTimestamp(ps, 4, submittedAt);
            setNullableTimestamp(ps, 5, decidedAt);
            ps.setString(6, decisionReason);
            ps.setLong(7, applicationId);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Application status update failed. Application id: " + applicationId);
            }
        }
    }

    public void updateReviewStatus(Connection conn, Long applicationId, ApplicationStatus status, String reviewerCode, String decisionReason, OffsetDateTime decidedAt) throws SQLException {
        String sql = """
            UPDATE loan_application SET
                status = ?,
                reviewer_code = ?,
                decision_reason = ?,
                decided_at = ?
            WHERE application_id = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, reviewerCode);
            ps.setString(3, decisionReason);
            setNullableTimestamp(ps, 4, decidedAt);
            ps.setLong(5, applicationId);

            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Review status update failed. Application id: " + applicationId);
            }
        }
    }

    public void updateStatusOnly(Connection conn, Long applicationId, ApplicationStatus status) throws SQLException {
        String sql = "UPDATE loan_application SET status = ? WHERE application_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, applicationId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Application status update failed. Application id: " + applicationId);
            }
        }
    }

    public Optional<LoanApplication> findById(Connection conn, Long applicationId) throws SQLException {
        String sql = "SELECT * FROM loan_application WHERE application_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<LoanApplication> findByApplicationNumber(Connection conn, String appNumber) throws SQLException {
        String sql = "SELECT * FROM loan_application WHERE application_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean hasActiveApplication(Connection conn, String customerId, ProductType product) throws SQLException {
        String sql = """
            SELECT 1 FROM loan_application
            WHERE customer_id = ? AND product = ? AND status IN ('DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL')
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.setString(2, product.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsByApplicationNumber(Connection conn, String appNumber) throws SQLException {
        String sql = "SELECT 1 FROM loan_application WHERE application_number = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<LoanApplication> findByCustomerId(Connection conn, String customerId) throws SQLException {
        String sql = "SELECT * FROM loan_application WHERE customer_id = ? ORDER BY created_at DESC";
        List<LoanApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public List<LoanApplication> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM loan_application ORDER BY created_at DESC";
        List<LoanApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public List<LoanApplication> filterApplications(Connection conn, ProductType product, ApplicationStatus status) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM loan_application WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (product != null) {
            sql.append("AND product = ? ");
            params.add(product.name());
        }
        if (status != null) {
            sql.append("AND status = ? ");
            params.add(status.name());
        }
        sql.append("ORDER BY created_at DESC");

        List<LoanApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public List<OverdueReviewItem> findOverdueReviews(Connection conn, LocalDate businessDate, int thresholdDays) throws SQLException {
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

    public List<LoanApplication> findDisbursedPendingCoreBanking(Connection conn) throws SQLException {
        String sql = """
            SELECT la.* FROM loan_application la
            LEFT JOIN ng_core_banking ncb ON la.application_id = ncb.application_id
            WHERE la.status = 'DISBURSED' AND ncb.application_id IS NULL
            ORDER BY la.application_id ASC
        """;
        List<LoanApplication> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private void setNullableTimestamp(PreparedStatement ps, int paramIndex, OffsetDateTime odt) throws SQLException {
        if (odt != null) {
            ps.setTimestamp(paramIndex, Timestamp.valueOf(odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
        } else {
            ps.setNull(paramIndex, Types.TIMESTAMP);
        }
    }

    private LoanApplication mapResultSet(ResultSet rs) throws SQLException {
        LoanApplication app = new LoanApplication();
        app.setApplicationId(rs.getLong("application_id"));
        app.setApplicationNumber(rs.getString("application_number"));
        app.setCustomerId(rs.getString("customer_id"));
        app.setProduct(ProductType.fromString(rs.getString("product")));
        app.setBranchCode(rs.getString("branch_code"));
        app.setBranchName(rs.getString("branch_name"));
        app.setRequestedAmount(rs.getBigDecimal("requested_amount"));
        app.setTenureMonths(rs.getInt("tenure_months"));
        app.setMonthlyIncome(rs.getBigDecimal("monthly_income"));
        app.setExistingMonthlyObligations(rs.getBigDecimal("existing_monthly_obligations"));
        app.setAnnualInterestRate(rs.getBigDecimal("annual_interest_rate"));
        app.setProposedEmi(rs.getBigDecimal("proposed_emi"));
        app.setFoirPercentage(rs.getBigDecimal("foir_percentage"));
        app.setEligible(rs.getBoolean("eligible"));
        app.setEligibilityReasons(rs.getString("eligibility_reasons"));
        app.setStatus(ApplicationStatus.fromString(rs.getString("status")));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            app.setCreatedAt(created.toInstant().atOffset(ZoneOffset.UTC));
        }
        Timestamp submitted = rs.getTimestamp("submitted_at");
        if (submitted != null) {
            app.setSubmittedAt(submitted.toInstant().atOffset(ZoneOffset.UTC));
        }
        Timestamp decided = rs.getTimestamp("decided_at");
        if (decided != null) {
            app.setDecidedAt(decided.toInstant().atOffset(ZoneOffset.UTC));
        }
        app.setReviewerCode(rs.getString("reviewer_code"));
        app.setDecisionReason(rs.getString("decision_reason"));
        return app;
    }
}
