package com.los.dao;

import com.los.model.UtilityResult;
import com.los.model.UtilityRun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw JDBC DAO for Utility Execution Runs.
 */
public class UtilityRunDAO {

    public Long insert(Connection conn, UtilityRun run) throws SQLException {
        String sql = """
            INSERT INTO utility_run (utility_name, started_at, completed_at, result, applications_processed, alerts_created, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, run.getUtilityName());
            OffsetDateTime start = run.getStartedAt() != null ? run.getStartedAt() : OffsetDateTime.now();
            ps.setTimestamp(2, Timestamp.valueOf(start.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));

            if (run.getCompletedAt() != null) {
                ps.setTimestamp(3, Timestamp.valueOf(run.getCompletedAt().atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));
            } else {
                ps.setNull(3, Types.TIMESTAMP);
            }

            ps.setString(4, run.getResult() != null ? run.getResult().name() : UtilityResult.SUCCESS.name());
            ps.setInt(5, run.getApplicationsProcessed());
            ps.setInt(6, run.getAlertsCreated());
            ps.setString(7, run.getErrorMessage());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    run.setRunId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated run_id.");
    }

    public List<UtilityRun> findRecent(Connection conn, int limit) throws SQLException {
        String sql = "SELECT * FROM utility_run ORDER BY started_at DESC LIMIT ?";
        List<UtilityRun> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UtilityRun r = new UtilityRun();
                    r.setRunId(rs.getLong("run_id"));
                    r.setUtilityName(rs.getString("utility_name"));
                    Timestamp sTs = rs.getTimestamp("started_at");
                    if (sTs != null) {
                        r.setStartedAt(sTs.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    Timestamp cTs = rs.getTimestamp("completed_at");
                    if (cTs != null) {
                        r.setCompletedAt(cTs.toInstant().atOffset(ZoneOffset.UTC));
                    }
                    r.setResult(UtilityResult.valueOf(rs.getString("result")));
                    r.setApplicationsProcessed(rs.getInt("applications_processed"));
                    r.setAlertsCreated(rs.getInt("alerts_created"));
                    r.setErrorMessage(rs.getString("error_message"));
                    list.add(r);
                }
            }
        }
        return list;
    }
}
