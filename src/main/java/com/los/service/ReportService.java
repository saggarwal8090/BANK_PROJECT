package com.los.service;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.dao.ReportsDAO;
import com.los.model.ApplicationRegisterItem;
import com.los.model.ApplicationStatus;
import com.los.model.CustomerWithoutDisbursementItem;
import com.los.model.DataAccessException;
import com.los.model.DisbursementSummaryItem;
import com.los.model.OverdueReviewItem;
import com.los.model.PipelineSummaryItem;
import com.los.model.ProductType;
import com.los.model.RepeatApplicantItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service orchestrating reporting queries and analytics.
 */
public class ReportService {

    private final ReportsDAO reportsDAO;
    private final AppConfig config;

    public ReportService() {
        this.reportsDAO = new ReportsDAO();
        this.config = AppConfig.getInstance();
    }

    public ReportService(ReportsDAO reportsDAO, AppConfig config) {
        this.reportsDAO = reportsDAO;
        this.config = config;
    }

    public List<ApplicationRegisterItem> getApplicationRegister(ProductType product, ApplicationStatus status) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getApplicationRegister(conn, product, status);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Application Register: " + e.getMessage(), e);
        }
    }

    public List<PipelineSummaryItem> getProductPipelineSummary() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getProductPipelineSummary(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Product Pipeline Summary: " + e.getMessage(), e);
        }
    }

    public List<PipelineSummaryItem> getBranchPipelineSummary() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getBranchPipelineSummary(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Branch Pipeline Summary: " + e.getMessage(), e);
        }
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryDaily(OffsetDateTime start, OffsetDateTime end) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getDisbursementSummaryDaily(conn, start, end);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Daily Disbursement Summary: " + e.getMessage(), e);
        }
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryMonthly(OffsetDateTime start, OffsetDateTime end) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getDisbursementSummaryMonthly(conn, start, end);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Monthly Disbursement Summary: " + e.getMessage(), e);
        }
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryQuarterly(OffsetDateTime start, OffsetDateTime end) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getDisbursementSummaryQuarterly(conn, start, end);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Quarterly Disbursement Summary: " + e.getMessage(), e);
        }
    }

    public List<DisbursementSummaryItem> getDisbursementSummaryBranch(OffsetDateTime start, OffsetDateTime end) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getDisbursementSummaryBranch(conn, start, end);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Branch Disbursement Summary: " + e.getMessage(), e);
        }
    }

    public List<OverdueReviewItem> getOverdueReviews() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            LocalDate bDate = config.getBusinessDate();
            int threshold = config.getOverdueThresholdDays();
            return reportsDAO.getOverdueReviews(conn, bDate, threshold);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Overdue Reviews report: " + e.getMessage(), e);
        }
    }

    public List<CustomerWithoutDisbursementItem> getCustomersWithoutDisbursement() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getCustomersWithoutDisbursement(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Customers Without Disbursement report: " + e.getMessage(), e);
        }
    }

    public List<RepeatApplicantItem> getRepeatApplicants() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return reportsDAO.getRepeatApplicants(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to generate Repeat Applicants report: " + e.getMessage(), e);
        }
    }
}
