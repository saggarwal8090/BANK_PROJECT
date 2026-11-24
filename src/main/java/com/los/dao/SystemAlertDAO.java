package com.los.dao;

import com.los.model.SystemAlert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw JDBC DAO for System Alerts (Idempotent insertion).
 */
public class SystemAlertDAO {

    /**
     * Inserts an alert idempotently.
     *
     * @return true if a new alert was inserted, false if already existed (conflict ignored).
     */
    public boolean insertIdempotent(Connection conn, SystemAlert alert) throws SQLException {
        String sql = """
            INSERT INTO system_alerts (application_id, alert_type, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT (application_id, alert_type) DO NOTHING
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, alert.getApplicationId());
            ps.setString(2, alert.getAlertType());
            OffsetDateTime dt = alert.getCreatedAt() != null ? alert.getCreatedAt() : OffsetDateTime.now();
            ps.setTimestamp(3, Timestamp.valueOf(dt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));

            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    public List<SystemAlert> findByApplicationId(Connection conn, Long applicationId) throws SQLException {
        String sql = "SELECT * FROM system_alerts WHERE application_id = ? ORDER BY created_at DESC";
        List<SystemAlert> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SystemAlert a = new SystemAlert();
                    a.setAlertId(rs.getLong("alert_id"));
                    a.setApplicationId(rs.getLong("application_id"));
                    a.setAlertType(rs.getString("alert_type"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) {
                        a.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    list.add(a);
                }
            }
        }
        return list;
    }

    public List<SystemAlert> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM system_alerts ORDER BY created_at DESC";
        List<SystemAlert> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SystemAlert a = new SystemAlert();
                a.setAlertId(rs.getLong("alert_id"));
                a.setApplicationId(rs.getLong("application_id"));
                a.setAlertType(rs.getString("alert_type"));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) {
                    a.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
                }
                list.add(a);
            }
        }
        return list;
    }
}
