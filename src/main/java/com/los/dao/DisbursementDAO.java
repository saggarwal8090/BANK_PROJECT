package com.los.dao;

import com.los.model.Disbursement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Raw JDBC DAO for Disbursement operations.
 */
public class DisbursementDAO {

    public Long insert(Connection conn, Disbursement disbursement) throws SQLException {
        String sql = """
            INSERT INTO disbursement (application_id, reference, amount, disbursed_at)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, disbursement.getApplicationId());
            ps.setString(2, disbursement.getReference());
            ps.setBigDecimal(3, disbursement.getAmount());
            OffsetDateTime dt = disbursement.getDisbursedAt() != null ? disbursement.getDisbursedAt() : OffsetDateTime.now();
            ps.setTimestamp(4, Timestamp.valueOf(dt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    disbursement.setDisbursementId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated disbursement_id.");
    }

    public Optional<Disbursement> findByApplicationId(Connection conn, Long applicationId) throws SQLException {
        String sql = "SELECT * FROM disbursement WHERE application_id = ?";
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

    public boolean existsByReference(Connection conn, String reference) throws SQLException {
        String sql = "SELECT 1 FROM disbursement WHERE reference = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Disbursement mapResultSet(ResultSet rs) throws SQLException {
        Disbursement d = new Disbursement();
        d.setDisbursementId(rs.getLong("disbursement_id"));
        d.setApplicationId(rs.getLong("application_id"));
        d.setReference(rs.getString("reference"));
        d.setAmount(rs.getBigDecimal("amount"));
        Timestamp ts = rs.getTimestamp("disbursed_at");
        if (ts != null) {
            d.setDisbursedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }
        return d;
    }
}
