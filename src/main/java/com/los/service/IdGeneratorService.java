package com.los.service;

import com.los.dao.CustomerDAO;
import com.los.dao.DisbursementDAO;
import com.los.dao.LoanApplicationDAO;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Cryptographically Secure ID Generator Service.
 * Implements collision-resistant identifier generation with active DB existence retry loops.
 */
public class IdGeneratorService {

    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_RETRIES = 15;

    private final SecureRandom random = new SecureRandom();
    private final CustomerDAO customerDAO;
    private final LoanApplicationDAO applicationDAO;
    private final DisbursementDAO disbursementDAO;

    public IdGeneratorService() {
        this.customerDAO = new CustomerDAO();
        this.applicationDAO = new LoanApplicationDAO();
        this.disbursementDAO = new DisbursementDAO();
    }

    public IdGeneratorService(CustomerDAO customerDAO, LoanApplicationDAO applicationDAO, DisbursementDAO disbursementDAO) {
        this.customerDAO = customerDAO;
        this.applicationDAO = applicationDAO;
        this.disbursementDAO = disbursementDAO;
    }

    /**
     * Generates a unique Customer ID in format CUST-XXXXXX with database collision verification.
     */
    public String generateCustomerId(Connection conn) throws SQLException {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String candidate = "CUST-" + generateRandomString(CODE_LENGTH);
            if (!customerDAO.existsById(conn, candidate)) {
                return candidate;
            }
        }
        throw new SQLException("Failed to generate a unique Customer ID after " + MAX_RETRIES + " attempts.");
    }

    /**
     * Generates a unique Loan Application Number in format APP-YYYY-XXXXXX with database collision verification.
     */
    public String generateApplicationNumber(Connection conn, LocalDate businessDate) throws SQLException {
        int year = (businessDate != null) ? businessDate.getYear() : LocalDate.now().getYear();
        for (int i = 0; i < MAX_RETRIES; i++) {
            String candidate = String.format("APP-%d-%s", year, generateRandomString(CODE_LENGTH));
            if (!applicationDAO.existsByApplicationNumber(conn, candidate)) {
                return candidate;
            }
        }
        throw new SQLException("Failed to generate a unique Application Number after " + MAX_RETRIES + " attempts.");
    }

    /**
     * Generates a unique Disbursement Reference in format DISB-YYYYMMDD-XXXXXX with database collision verification.
     */
    public String generateDisbursementReference(Connection conn, LocalDate businessDate) throws SQLException {
        LocalDate date = (businessDate != null) ? businessDate : LocalDate.now();
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < MAX_RETRIES; i++) {
            String candidate = String.format("DISB-%s-%s", dateStr, generateRandomString(CODE_LENGTH));
            if (!disbursementDAO.existsByReference(conn, candidate)) {
                return candidate;
            }
        }
        throw new SQLException("Failed to generate a unique Disbursement Reference after " + MAX_RETRIES + " attempts.");
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = random.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(idx));
        }
        return sb.toString();
    }
}
