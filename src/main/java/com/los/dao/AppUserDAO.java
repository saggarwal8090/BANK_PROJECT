package com.los.dao;

import com.los.model.AppUser;
import com.los.model.UserRole;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Raw JDBC DAO for managing system users and roles.
 */
public class AppUserDAO {

    public Long insert(Connection conn, AppUser user) throws SQLException {
        String sql = """
            INSERT INTO app_user (username, full_name, role, email, branch_code, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getRole().name());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getBranchCode());
            OffsetDateTime dt = user.getCreatedAt() != null ? user.getCreatedAt() : OffsetDateTime.now();
            ps.setTimestamp(6, Timestamp.valueOf(dt.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()));

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    user.setUserId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to retrieve generated user_id");
    }

    public Optional<AppUser> findByUsername(Connection conn, String username) throws SQLException {
        String sql = "SELECT * FROM app_user WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<AppUser> findAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM app_user ORDER BY user_id ASC";
        List<AppUser> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    private AppUser mapResultSet(ResultSet rs) throws SQLException {
        AppUser u = new AppUser();
        u.setUserId(rs.getLong("user_id"));
        u.setUsername(rs.getString("username"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(UserRole.fromString(rs.getString("role")));
        u.setEmail(rs.getString("email"));
        u.setBranchCode(rs.getString("branch_code"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            u.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }
        return u;
    }
}
