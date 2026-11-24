package com.los.dao;

import com.los.model.ApplicationHistory;
import com.los.model.ApplicationStatus;

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
 * Raw JDBC DAO for Application History / Audit Trail.
 */
public class ApplicationHistoryDAO {

    public void insert(Connection conn, ApplicationHistory history) throws SQLException {
        String sql = """
            INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, history.getApplicationId());
            ps.setString(2, history.getAction());
            ps.setString(3, history.getOldStatus() != null ? history.getOldStatus().name() : null);
            ps.setString(4, history.getNewStatus().name());
            ps.setString(5, history.getPerformedBy());
            OffsetDateTime dt = history.getPerformedAt() != null ? history.getPerformedAt() : OffsetDateTime.now();
            ps.setTimestamp(6, Timestamp.valueOf(dt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            ps.setString(7, history.getRemarks());

            ps.executeUpdate();
        }
    }

    public List<ApplicationHistory> findByApplicationId(Connection conn, Long applicationId) throws SQLException {
        String sql = "SELECT * FROM application_history WHERE application_id = ? ORDER BY performed_at ASC";
        List<ApplicationHistory> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ApplicationHistory h = new ApplicationHistory();
                    h.setHistoryId(rs.getLong("history_id"));
                    h.setApplicationId(rs.getLong("application_id"));
                    h.setAction(rs.getString("action"));
                    String oldS = rs.getString("old_status");
                    if (oldS != null) {
                        h.setOldStatus(ApplicationStatus.fromString(oldS));
                    }
                    h.setNewStatus(ApplicationStatus.fromString(rs.getString("new_status")));
                    h.setPerformedBy(rs.getString("performed_by"));
                    Timestamp ts = rs.getTimestamp("performed_at");
                    if (ts != null) {
                        h.setPerformedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    h.setRemarks(rs.getString("remarks"));
                    list.add(h);
                }
            }
        }
        return list;
    }
}
