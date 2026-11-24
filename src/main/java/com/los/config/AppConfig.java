package com.los.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Enterprise Application Configuration for Mini Loan Origination System (LOS).
 * Reads configuration from Environment Variables (with priority for Vercel/Cloud deployment)
 * and falls back to config.properties (from working directory or classpath).
 */
public class AppConfig {

    private static volatile AppConfig instance;
    private final Properties properties = new Properties();

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int serverPort;
    private final LocalDate businessDate;

    // Supabase Cloud Project Configuration
    private final String supabaseUrl;
    private final String supabasePublishableKey;
    private final String supabaseSecretKey;
    private final String supabaseJwksUrl;

    // Eligibility Boundaries
    private final int minAge;
    private final int maxAge;
    private final BigDecimal maxFoirPercentage;
    private final int overdueThresholdDays;

    // Product: PERSONAL
    private final BigDecimal personalMinAmount;
    private final BigDecimal personalMaxAmount;
    private final int personalMinTenure;
    private final int personalMaxTenure;
    private final BigDecimal personalMinIncome;
    private final BigDecimal personalInterestRate;
    private final boolean personalAllowCoBorrower;

    // Product: EDUCATION
    private final BigDecimal educationMinAmount;
    private final BigDecimal educationMaxAmount;
    private final int educationMinTenure;
    private final int educationMaxTenure;
    private final BigDecimal educationMinIncome;
    private final BigDecimal educationInterestRate;
    private final boolean educationAllowCoBorrower;

    private AppConfig() {
        loadProperties();

        // Database settings: Env vars take precedence
        this.dbUrl = getEnvOrProperty("DB_URL", "db.url", "jdbc:postgresql://localhost:5432/postgres?sslmode=prefer");
        this.dbUser = getEnvOrProperty("DB_USER", "db.user", "postgres");
        this.dbPassword = getEnvOrProperty("DB_PASSWORD", "db.password", "postgres");

        // Supabase Dynamic Settings
        this.supabaseUrl = getEnvOrProperty("SUPABASE_URL", "supabase.url", "https://ciemfotaaupazeqxxuev.supabase.co");
        this.supabasePublishableKey = getEnvOrProperty("SUPABASE_PUBLISHABLE_KEY", "supabase.publishable_key", "sb_publishable_kX3L5-No3WzDIen5e9KmOw_VFI6gMol");
        this.supabaseSecretKey = getEnvOrProperty("SUPABASE_SECRET_KEY", "supabase.secret_key", "your_supabase_secret_key");
        this.supabaseJwksUrl = getEnvOrProperty("SUPABASE_JWKS_URL", "supabase.jwks_url", "https://ciemfotaaupazeqxxuev.supabase.co/auth/v1/.well-known/jwks.json");

        this.serverPort = Integer.parseInt(getEnvOrProperty("PORT", "server.port", "8085"));

        // Business Date: Defaults to current local date if not explicitly configured
        String bDateStr = getEnvOrProperty("BUSINESS_DATE", "app.business_date", "");
        if (bDateStr != null && !bDateStr.trim().isEmpty()) {
            this.businessDate = LocalDate.parse(bDateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            this.businessDate = LocalDate.now();
        }

        // Global Eligibility Boundaries
        this.minAge = Integer.parseInt(getProperty("eligibility.min_age", "21"));
        this.maxAge = Integer.parseInt(getProperty("eligibility.max_age", "60"));
        this.maxFoirPercentage = new BigDecimal(getProperty("eligibility.max_foir_percentage", "50.00"));
        this.overdueThresholdDays = Integer.parseInt(getProperty("utility.overdue_threshold_days", "2"));

        // Personal Loan Product Rules
        this.personalMinAmount = new BigDecimal(getProperty("product.personal.min_amount", "50000.00"));
        this.personalMaxAmount = new BigDecimal(getProperty("product.personal.max_amount", "500000.00"));
        this.personalMinTenure = Integer.parseInt(getProperty("product.personal.min_tenure_months", "12"));
        this.personalMaxTenure = Integer.parseInt(getProperty("product.personal.max_tenure_months", "60"));
        this.personalMinIncome = new BigDecimal(getProperty("product.personal.min_monthly_income", "20000.00"));
        this.personalInterestRate = new BigDecimal(getProperty("product.personal.annual_interest_rate", "12.00"));
        this.personalAllowCoBorrower = Boolean.parseBoolean(getProperty("product.personal.allow_co_borrower", "true"));

        // Education Loan Product Rules
        this.educationMinAmount = new BigDecimal(getProperty("product.education.min_amount", "100000.00"));
        this.educationMaxAmount = new BigDecimal(getProperty("product.education.max_amount", "1000000.00"));
        this.educationMinTenure = Integer.parseInt(getProperty("product.education.min_tenure_months", "12"));
        this.educationMaxTenure = Integer.parseInt(getProperty("product.education.max_tenure_months", "84"));
        this.educationMinIncome = new BigDecimal(getProperty("product.education.min_monthly_income", "15000.00"));
        this.educationInterestRate = new BigDecimal(getProperty("product.education.annual_interest_rate", "10.00"));
        this.educationAllowCoBorrower = Boolean.parseBoolean(getProperty("product.education.allow_co_borrower", "false"));
    }

    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    private void loadProperties() {
        // Try file in root directory first
        File file = new File("config.properties");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
                return;
            } catch (Exception ignored) {
            }
        }

        // Try classpath resource
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (Exception ignored) {
        }
    }

    private String getEnvOrProperty(String envVar, String propKey, String defaultValue) {
        String val = System.getenv(envVar);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        val = properties.getProperty(propKey);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        return defaultValue;
    }

    private String getProperty(String propKey, String defaultValue) {
        return properties.getProperty(propKey, defaultValue);
    }

    // Getters
    public String getDbUrl() { 
        if (dbUrl != null && !dbUrl.contains("connectTimeout")) {
            return dbUrl + (dbUrl.contains("?") ? "&" : "?") + "connectTimeout=3&socketTimeout=5";
        }
        return dbUrl; 
    }
    public String getDbUser() { return dbUser; }
    public String getDbPassword() { return dbPassword; }
    public String getSupabaseUrl() { return supabaseUrl; }
    public String getSupabasePublishableKey() { return supabasePublishableKey; }
    public String getSupabaseSecretKey() { return supabaseSecretKey; }
    public String getSupabaseJwksUrl() { return supabaseJwksUrl; }
    public int getServerPort() { return serverPort; }
    public LocalDate getBusinessDate() { return businessDate; }

    public int getMinAge() { return minAge; }
    public int getMaxAge() { return maxAge; }
    public BigDecimal getMaxFoirPercentage() { return maxFoirPercentage; }
    public int getOverdueThresholdDays() { return overdueThresholdDays; }

    public BigDecimal getPersonalMinAmount() { return personalMinAmount; }
    public BigDecimal getPersonalMaxAmount() { return personalMaxAmount; }
    public int getPersonalMinTenure() { return personalMinTenure; }
    public int getPersonalMaxTenure() { return personalMaxTenure; }
    public BigDecimal getPersonalMinIncome() { return personalMinIncome; }
    public BigDecimal getPersonalInterestRate() { return personalInterestRate; }
    public boolean isPersonalAllowCoBorrower() { return personalAllowCoBorrower; }

    public BigDecimal getEducationMinAmount() { return educationMinAmount; }
    public BigDecimal getEducationMaxAmount() { return educationMaxAmount; }
    public int getEducationMinTenure() { return educationMinTenure; }
    public int getEducationMaxTenure() { return educationMaxTenure; }
    public BigDecimal getEducationMinIncome() { return educationMinIncome; }
    public BigDecimal getEducationInterestRate() { return educationInterestRate; }
    public boolean isEducationAllowCoBorrower() { return educationAllowCoBorrower; }
}
