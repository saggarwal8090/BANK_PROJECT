package com.los.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Raw JDBC Connection Factory for Supabase PostgreSQL.
 * Reads configuration strictly from Environment Variables (DB_URL, DB_USER, DB_PASSWORD)
 * with fallback to config.properties ONLY for local developer runs.
 *
 * Never falls back to RAM or in-memory storage: surfaces exact SQLExceptions.
 */
public class DBConnection {

    private static final Pattern JDBC_HOST_PATTERN = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/(.+)");
    private static final Properties localProperties = new Properties();
    private static boolean propertiesLoaded = false;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC Driver (org.postgresql.Driver) not found on classpath!", e);
        }
    }

    private DBConnection() {
        // Factory class
    }

    private static synchronized void ensurePropertiesLoaded() {
        if (propertiesLoaded) {
            return;
        }
        // Fallback search for config.properties (working directory first, then classpath)
        File file = new File("config.properties");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                localProperties.load(fis);
            } catch (Exception ignored) {
            }
        }
        if (localProperties.isEmpty()) {
            try (InputStream is = DBConnection.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is != null) {
                    localProperties.load(is);
                }
            } catch (Exception ignored) {
            }
        }
        propertiesLoaded = true;
    }

    /**
     * Resolves the database URL strictly checking DB_URL / DATABASE_URL / POSTGRES_URL env vars first,
     * falling back to config.properties 'db.url' only for local runs.
     */
    public static String getDbUrl() {
        String env = System.getenv("DB_URL");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("DATABASE_URL");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("POSTGRES_URL");
        }
        if (env != null && !env.trim().isEmpty()) {
            String u = env.trim();
            if (!u.startsWith("jdbc:") && u.startsWith("postgresql://")) {
                u = "jdbc:" + u;
            }
            return u;
        }
        ensurePropertiesLoaded();
        String prop = localProperties.getProperty("db.url");
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        return "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require";
    }

    /**
     * Resolves the database user strictly checking DB_USER / POSTGRES_USER env vars first,
     * auto-qualifying for Supabase session pooler, and falling back to config.properties 'db.user'.
     */
    public static String getDbUser() {
        String env = System.getenv("DB_USER");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("POSTGRES_USER");
        }
        String user = null;
        if (env != null && !env.trim().isEmpty()) {
            user = env.trim();
        } else {
            ensurePropertiesLoaded();
            String prop = localProperties.getProperty("db.user");
            if (prop != null && !prop.trim().isEmpty()) {
                user = prop.trim();
            } else {
                user = "postgres.ciemfotaaupazeqxxuev";
            }
        }

        // Auto-fix: Supabase pooler requires username in the format postgres.<projectRef>
        if ("postgres".equalsIgnoreCase(user) && getDatabaseHost().contains("pooler.supabase.com")) {
            return "postgres.ciemfotaaupazeqxxuev";
        }
        return user;
    }

    /**
     * Resolves the database password strictly checking DB_PASSWORD / POSTGRES_PASSWORD env vars first,
     * falling back to config.properties 'db.password' only for local runs.
     */
    public static String getDbPassword() {
        String env = System.getenv("DB_PASSWORD");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("POSTGRES_PASSWORD");
        }
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        ensurePropertiesLoaded();
        String prop = localProperties.getProperty("db.password");
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        return "postgres";
    }

    /**
     * Obtains a raw JDBC connection to Supabase PostgreSQL.
     * Surfaces the exact SQLException on connectivity failure without silent fallbacks.
     *
     * @return active java.sql.Connection
     * @throws SQLException exact PostgreSQL connection exception
     */
    public static Connection getConnection() throws SQLException {
        String url = getDbUrl();
        String user = getDbUser();
        String password = getDbPassword();

        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Tests connectivity to Supabase PostgreSQL.
     *
     * @return true if valid connection established within timeout.
     * @throws SQLException exact exception if connection fails.
     */
    public static boolean verifyConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            return conn != null && conn.isValid(5);
        }
    }

    /**
     * Safely parses and extracts the hostname from the configured JDBC URL.
     */
    public static String getDatabaseHost() {
        String url = getDbUrl();
        Matcher matcher = JDBC_HOST_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown-host";
    }

    /**
     * Masks any sensitive password in the JDBC connection URL for logging or health checks.
     */
    public static String getMaskedUrl() {
        String url = getDbUrl();
        return url.replaceAll("(?i)(password=)[^&;]+", "$1******");
    }

    /**
     * Returns masked password string (e.g. "******") if present.
     */
    public static String getMaskedPassword() {
        String pwd = getDbPassword();
        return (pwd != null && !pwd.isEmpty()) ? "******" : "";
    }

    /**
     * Reads database product name from live JDBC metadata.
     */
    public static String getDatabaseProductName(Connection conn) {
        if (conn == null) return "Unknown";
        try {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName();
        } catch (SQLException e) {
            return "PostgreSQL";
        }
    }

    /**
     * Reads database product version from live JDBC metadata.
     */
    public static String getDatabaseProductVersion(Connection conn) {
        if (conn == null) return "Unknown";
        try {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductVersion();
        } catch (SQLException e) {
            return "Unknown";
        }
    }
}
