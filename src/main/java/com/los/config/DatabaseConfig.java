package com.los.config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Enterprise Database Configuration & Raw JDBC Connection Factory.
 * Manages PostgreSQL connections (specifically tuned for Supabase Session Pooler).
 */
public class DatabaseConfig {

    static {
        try {
            Class.forName("org.postgresql.Driver");
            DriverManager.setLoginTimeout(3);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver not found on classpath!", e);
        }
    }

    private DatabaseConfig() {
        // Utility / factory class
    }

    /**
     * Obtains a new standard JDBC Connection using configured credentials.
     *
     * @return active java.sql.Connection
     * @throws SQLException if a connection cannot be established
     */
    public static Connection getConnection() throws SQLException {
        AppConfig config = AppConfig.getInstance();
        DriverManager.setLoginTimeout(3);
        return DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
    }

    /**
     * Tests the database connectivity with fast failover.
     * Checks JDBC connection first; if remote pooler is authenticating, verifies Supabase Cloud REST endpoint.
     *
     * @return true if connection succeeded, false otherwise.
     */
    public static boolean testConnection() {
        try {
            DriverManager.setLoginTimeout(3);
            try (Connection conn = getConnection()) {
                return conn != null && conn.isValid(3);
            }
        } catch (Exception e) {
            // Check Supabase Cloud Live status via SupabaseClient
            try {
                SupabaseClient supabase = new SupabaseClient();
                return supabase.testConnection();
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * Executes the initial schema.sql if the database tables need to be created.
     */
    public static void initializeDatabase() throws SQLException {
        InputStream is = DatabaseConfig.class.getClassLoader().getResourceAsStream("schema.sql");
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("schema.sql");
        }
        if (is == null) {
            System.err.println("[DatabaseConfig] schema.sql not found on classpath. Skipping auto-init.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Ignore comment lines
                if (line.trim().startsWith("--")) {
                    continue;
                }
                sb.append(line).append("\n");
            }
        } catch (Exception e) {
            throw new SQLException("Failed to read schema.sql from resources", e);
        }

        String fullSql = sb.toString();
        // Split on semicolon followed by newline/whitespace to execute distinct statements
        String[] statements = fullSql.split(";\\s*(\\r?\\n)+");

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }
}
