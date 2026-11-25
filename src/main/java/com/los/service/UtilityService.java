package com.los.service;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.dao.ApplicationHistoryDAO;
import com.los.dao.LoanApplicationDAO;
import com.los.dao.NgCoreBankingDAO;
import com.los.dao.SystemAlertDAO;
import com.los.dao.UtilityRunDAO;
import com.los.model.ApplicationHistory;
import com.los.model.ApplicationStatus;
import com.los.model.LoanApplication;
import com.los.model.OverdueReviewItem;
import com.los.model.SystemAlert;
import com.los.model.UtilityResult;
import com.los.model.UtilityRun;
import com.los.utility.CoreBankingPushUtility;
import com.los.utility.OverdueReviewMonitor;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Enterprise Utility Service orchestrating batch pipeline operations.
 * Enforces strict transactional integrity: every write operation using
 * conn.setAutoCommit(false) explicitly calls conn.commit() on success
 * and conn.rollback() on exception.
 */
public class UtilityService {

    private final LoanApplicationDAO applicationDAO;
    private final SystemAlertDAO alertDAO;
    private final UtilityRunDAO runDAO;
    private final OverdueReviewMonitor overdueMonitor;
    private final CoreBankingPushUtility coreBankingPushUtility;
    private final AppConfig config;

    public UtilityService() {
        this.applicationDAO = new LoanApplicationDAO();
        this.alertDAO = new SystemAlertDAO();
        this.runDAO = new UtilityRunDAO();
        this.overdueMonitor = new OverdueReviewMonitor();
        this.coreBankingPushUtility = new CoreBankingPushUtility();
        this.config = AppConfig.getInstance();
    }

    /**
     * Executes the Overdue Review Monitor batch pipeline with strict transaction commit.
     */
    public UtilityRun runOverdueReviewMonitor(LocalDate businessDate, int thresholdDays) throws SQLException {
        UtilityRun runRecord = new UtilityRun("OVERDUE_REVIEW_MONITOR", OffsetDateTime.now());
        int processedCount = 0;
        int alertsCreated = 0;

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<LoanApplication> underReview = applicationDAO.filterApplications(conn, null, ApplicationStatus.UNDER_REVIEW);
                processedCount = underReview.size();

                List<OverdueReviewItem> overdue = applicationDAO.findOverdueReviews(conn, businessDate, thresholdDays);
                for (OverdueReviewItem item : overdue) {
                    SystemAlert alert = new SystemAlert(item.getApplicationId(), "REVIEW_OVERDUE");
                    if (alertDAO.insertIdempotent(conn, alert)) {
                        alertsCreated++;
                    }
                }

                runRecord.setCompletedAt(OffsetDateTime.now());
                runRecord.setResult(UtilityResult.SUCCESS);
                runRecord.setApplicationsProcessed(processedCount);
                runRecord.setAlertsCreated(alertsCreated);

                runDAO.insert(conn, runRecord);
                conn.commit();
                return runRecord;
            } catch (Exception e) {
                conn.rollback();
                throw new SQLException("Failed overdue review monitor batch: " + e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Executes Core Banking CBS push for all disbursed loans with strict transaction commit.
     */
    public UtilityRun runCoreBankingPush() {
        return coreBankingPushUtility.executeJob();
    }

    /**
     * Runs all automated pipeline utilities sequentially.
     */
    public void runAllUtilities() throws SQLException {
        runOverdueReviewMonitor(config.getBusinessDate(), config.getOverdueThresholdDays());
        runCoreBankingPush();
    }
}
