package com.los.dao;

import com.los.model.NgCoreBanking;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Raw JDBC DAO for Next-Gen Core Banking table.
 */
public class NgCoreBankingDAO {

    public Long insert(Connection conn, NgCoreBanking ncb) throws SQLException {
        String sql = """
            INSERT INTO ng_core_banking (
                application_id, customer_id, customer_name, address, dob, kyc_details,
                approved_amount, disbursed_amount, disbursed_date, synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ncb.getApplicationId());
            ps.setString(2, ncb.getCustomerId());
            ps.setString(3, ncb.getCustomerName());
            ps.setString(4, ncb.getAddress());
            ps.setDate(5, Date.valueOf(ncb.getDob()));
            ps.setString(6, ncb.getKycDetails());
            ps.setBigDecimal(7, ncb.getApprovedAmount());
            ps.setBigDecimal(8, ncb.getDisbursedAmount());
            ps.setDate(9, Date.valueOf(ncb.getDisbursedDate()));
            OffsetDateTime synced = ncb.getSyncedAt() != null ? ncb.getSyncedAt() : OffsetDateTime.now();
            ps.setTimestamp(10, Timestamp.valueOf(synced.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    ncb.setCoreId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated core_id.");
    }

    public Optional<NgCoreBanking> findByApplicationId(Connection conn, Long applicationId) throws SQLException {
        String sql = "SELECT * FROM ng_core_banking WHERE application_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    NgCoreBanking ncb = new NgCoreBanking();
                    ncb.setCoreId(rs.getLong("core_id"));
                    ncb.setApplicationId(rs.getLong("application_id"));
                    ncb.setCustomerId(rs.getString("customer_id"));
                    ncb.setCustomerName(rs.getString("customer_name"));
                    ncb.setAddress(rs.getString("address"));
                    Date dob = rs.getDate("dob");
                    if (dob != null) {
                        ncb.setDob(dob.toLocalDate());
                    }
                    ncb.setKycDetails(rs.getString("kyc_details"));
                    ncb.setApprovedAmount(rs.getBigDecimal("approved_amount"));
                    ncb.setDisbursedAmount(rs.getBigDecimal("disbursed_amount"));
                    Date dDate = rs.getDate("disbursed_date");
                    if (dDate != null) {
                        ncb.setDisbursedDate(dDate.toLocalDate());
                    }
                    Timestamp synced = rs.getTimestamp("synced_at");
                    if (synced != null) {
                        ncb.setSyncedAt(synced.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    return Optional.of(ncb);
                }
            }
        }
        return Optional.empty();
    }
}
