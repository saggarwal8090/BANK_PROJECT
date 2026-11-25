package com.los.utility;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.config.IndianCurrencyFormatter;
import com.los.dao.LoanApplicationDAO;
import com.los.dao.SystemAlertDAO;
import com.los.dao.UtilityRunDAO;
import com.los.model.ApplicationStatus;
import com.los.model.LoanApplication;
import com.los.model.OverdueReviewItem;
import com.los.model.SystemAlert;
import com.los.model.UtilityResult;
import com.los.model.UtilityRun;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Background Utility: Overdue Review Monitor.
 *
 * Scans for applications in UNDER_REVIEW status waiting > 2 calendar days.
 * - Leaves status unchanged.
 * - Idempotently logs REVIEW_OVERDUE alerts in system_alerts.
 * - Audits run metrics in utility_run.
 * - Generates pending_reviews_YYYYMMDD.csv with OverdueFlag (Yes/No).
 */
public class OverdueReviewMonitor implements Runnable {

    private final AppConfig config;
    private final LoanApplicationDAO applicationDAO;
    private final SystemAlertDAO alertDAO;
    private final UtilityRunDAO runDAO;

    public OverdueReviewMonitor() {
        this.config = AppConfig.getInstance();
        this.applicationDAO = new LoanApplicationDAO();
        this.alertDAO = new SystemAlertDAO();
        this.runDAO = new UtilityRunDAO();
    }

    public OverdueReviewMonitor(AppConfig config, LoanApplicationDAO applicationDAO, SystemAlertDAO alertDAO, UtilityRunDAO runDAO) {
        this.config = config;
        this.applicationDAO = applicationDAO;
        this.alertDAO = alertDAO;
        this.runDAO = runDAO;
    }

    @Override
    public void run() {
        executeJob();
    }

    /**
     * Executes the monitor job and returns the UtilityRun audit record.
     */
    public UtilityRun executeJob() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        LocalDate businessDate = config.getBusinessDate();
        int thresholdDays = config.getOverdueThresholdDays();

        UtilityRun runRecord = new UtilityRun("OVERDUE_REVIEW_MONITOR", startedAt);
        int processedCount = 0;
        int alertsCreated = 0;

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Find all applications currently UNDER_REVIEW
                List<LoanApplication> underReviewApps = applicationDAO.filterApplications(conn, null, ApplicationStatus.UNDER_REVIEW);
                processedCount = underReviewApps.size();

                // Find strictly overdue ones (> thresholdDays)
                List<OverdueReviewItem> overdueItems = applicationDAO.findOverdueReviews(conn, businessDate, thresholdDays);

                // Insert idempotent alerts for overdue apps
                for (OverdueReviewItem item : overdueItems) {
                    SystemAlert alert = new SystemAlert(item.getApplicationId(), "REVIEW_OVERDUE");
                    boolean inserted = alertDAO.insertIdempotent(conn, alert);
                    if (inserted) {
                        alertsCreated++;
                    }
                }

                // Generate CSV of all pending reviews with OverdueFlag
                generateCsvReport(underReviewApps, businessDate, thresholdDays);

                runRecord.setCompletedAt(OffsetDateTime.now());
                runRecord.setResult(UtilityResult.SUCCESS);
                runRecord.setApplicationsProcessed(processedCount);
                runRecord.setAlertsCreated(alertsCreated);
                runRecord.setErrorMessage(null);

                runDAO.insert(conn, runRecord);
                conn.commit();
                System.out.printf("[OverdueReviewMonitor] Completed. Processed: %d, Overdue Alerts Created: %d%n",
                        processedCount, alertsCreated);
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[OverdueReviewMonitor] Execution failed: " + e.getMessage());
            runRecord.setCompletedAt(OffsetDateTime.now());
            runRecord.setResult(UtilityResult.FAILURE);
            runRecord.setApplicationsProcessed(processedCount);
            runRecord.setAlertsCreated(alertsCreated);
            runRecord.setErrorMessage(e.getMessage());

            try (Connection conn = DatabaseConfig.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    runDAO.insert(conn, runRecord);
                    conn.commit();
                } catch (Exception rollbackEx) {
                    conn.rollback();
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (Exception ignored) {
            }
        }
        return runRecord;
    }

    /**
     * Generates pending_reviews_YYYYMMDD.csv.
     */
    public File generateCsvReport(List<LoanApplication> apps, LocalDate businessDate, int thresholdDays) {
        String dateStr = businessDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = "pending_reviews_" + dateStr + ".csv";

        // Determine destination directory: /tmp on serverless or current working directory
        File targetDir = new File(System.getProperty("java.io.tmpdir"));
        if (!targetDir.canWrite()) {
            targetDir = new File(".");
        }
        File targetFile = new File(targetDir, filename);

        String[] headers = {
            "ApplicationNumber", "CustomerId", "Product", "BranchCode", "BranchName",
            "RequestedAmount", "SubmittedDate", "DaysWaiting", "OverdueFlag"
        };

        List<String[]> rows = new ArrayList<>();
        for (LoanApplication app : apps) {
            long daysWaiting = 0;
            if (app.getSubmittedAt() != null) {
                LocalDate submittedDate = app.getSubmittedAt().toLocalDate();
                daysWaiting = java.time.temporal.ChronoUnit.DAYS.between(submittedDate, businessDate);
            }
            String overdueFlag = (daysWaiting > thresholdDays) ? "Yes" : "No";

            rows.add(new String[]{
                app.getApplicationNumber(),
                app.getCustomerId(),
                app.getProduct().name(),
                app.getBranchCode(),
                app.getBranchName(),
                IndianCurrencyFormatter.format(app.getRequestedAmount()),
                app.getSubmittedAt() != null ? app.getSubmittedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
                String.valueOf(daysWaiting),
                overdueFlag
            });
        }

        try {
            CsvExporter.exportToFile(targetFile, headers, rows);
            // Also write to current directory if not already there for easy local access
            File localCopy = new File(filename);
            if (!localCopy.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                CsvExporter.exportToFile(localCopy, headers, rows);
            }
        } catch (Exception e) {
            System.err.println("[OverdueReviewMonitor] Failed to write CSV file: " + e.getMessage());
        }

        return targetFile;
    }

    /**
     * Generates the CSV content as an in-memory string for web download endpoints.
     */
    public String generateCsvString() {
        LocalDate businessDate = config.getBusinessDate();
        int thresholdDays = config.getOverdueThresholdDays();

        try (Connection conn = DatabaseConfig.getConnection()) {
            List<LoanApplication> apps = applicationDAO.filterApplications(conn, null, ApplicationStatus.UNDER_REVIEW);
            String[] headers = {
                "ApplicationNumber", "CustomerId", "Product", "BranchCode", "BranchName",
                "RequestedAmount", "SubmittedDate", "DaysWaiting", "OverdueFlag"
            };

            List<String[]> rows = new ArrayList<>();
            for (LoanApplication app : apps) {
                long daysWaiting = 0;
                if (app.getSubmittedAt() != null) {
                    LocalDate submittedDate = app.getSubmittedAt().toLocalDate();
                    daysWaiting = java.time.temporal.ChronoUnit.DAYS.between(submittedDate, businessDate);
                }
                String overdueFlag = (daysWaiting > thresholdDays) ? "Yes" : "No";

                rows.add(new String[]{
                    app.getApplicationNumber(),
                    app.getCustomerId(),
                    app.getProduct().name(),
                    app.getBranchCode(),
                    app.getBranchName(),
                    IndianCurrencyFormatter.format(app.getRequestedAmount()),
                    app.getSubmittedAt() != null ? app.getSubmittedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : "",
                    String.valueOf(daysWaiting),
                    overdueFlag
                });
            }
            return CsvExporter.exportToString(headers, rows);
        } catch (Exception e) {
            return "Error generating CSV: " + e.getMessage();
        }
    }
}
