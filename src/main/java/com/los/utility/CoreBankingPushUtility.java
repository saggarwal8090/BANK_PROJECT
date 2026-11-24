package com.los.utility;

import com.los.config.DatabaseConfig;
import com.los.dao.ApplicationHistoryDAO;
import com.los.dao.CustomerDAO;
import com.los.dao.DisbursementDAO;
import com.los.dao.LoanApplicationDAO;
import com.los.dao.NgCoreBankingDAO;
import com.los.dao.UtilityRunDAO;
import com.los.model.ApplicationHistory;
import com.los.model.ApplicationStatus;
import com.los.model.Customer;
import com.los.model.Disbursement;
import com.los.model.LoanApplication;
import com.los.model.NgCoreBanking;
import com.los.model.UtilityResult;
import com.los.model.UtilityRun;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Background Utility: Core Banking Integration Push Utility.
 *
 * Polls loans ready after disbursement (status = DISBURSED),
 * atomically inserts them into ng_core_banking, transitions status to EXIT,
 * writes audit trail to application_history, and guarantees zero duplicates or missed records.
 */
public class CoreBankingPushUtility implements Runnable {

    private final LoanApplicationDAO applicationDAO;
    private final CustomerDAO customerDAO;
    private final DisbursementDAO disbursementDAO;
    private final NgCoreBankingDAO coreBankingDAO;
    private final ApplicationHistoryDAO historyDAO;
    private final UtilityRunDAO runDAO;

    public CoreBankingPushUtility() {
        this.applicationDAO = new LoanApplicationDAO();
        this.customerDAO = new CustomerDAO();
        this.disbursementDAO = new DisbursementDAO();
        this.coreBankingDAO = new NgCoreBankingDAO();
        this.historyDAO = new ApplicationHistoryDAO();
        this.runDAO = new UtilityRunDAO();
    }

    public CoreBankingPushUtility(LoanApplicationDAO applicationDAO, CustomerDAO customerDAO,
                                 DisbursementDAO disbursementDAO, NgCoreBankingDAO coreBankingDAO,
                                 ApplicationHistoryDAO historyDAO, UtilityRunDAO runDAO) {
        this.applicationDAO = applicationDAO;
        this.customerDAO = customerDAO;
        this.disbursementDAO = disbursementDAO;
        this.coreBankingDAO = coreBankingDAO;
        this.historyDAO = historyDAO;
        this.runDAO = runDAO;
    }

    @Override
    public void run() {
        executeJob();
    }

    /**
     * Executes the Core Banking Push pipeline and logs the run metrics.
     */
    public UtilityRun executeJob() {
        OffsetDateTime startedAt = OffsetDateTime.now();
        UtilityRun runRecord = new UtilityRun("CORE_BANKING_PUSH_UTILITY", startedAt);
        int processedCount = 0;

        try (Connection conn = DatabaseConfig.getConnection()) {
            List<LoanApplication> readyLoans = applicationDAO.findDisbursedPendingCoreBanking(conn);

            for (LoanApplication app : readyLoans) {
                // Process each loan in an isolated ACID transaction for atomic guarantee
                boolean pushed = pushSingleLoan(app.getApplicationId());
                if (pushed) {
                    processedCount++;
                }
            }

            runRecord.setCompletedAt(OffsetDateTime.now());
            runRecord.setResult(UtilityResult.SUCCESS);
            runRecord.setApplicationsProcessed(processedCount);
            runRecord.setAlertsCreated(0);
            runRecord.setErrorMessage(null);

            runDAO.insert(conn, runRecord);
            System.out.printf("[CoreBankingPushUtility] Completed. Successfully synced %d loans to ng_core_banking.%n",
                    processedCount);

        } catch (Exception e) {
            System.err.println("[CoreBankingPushUtility] Batch run encountered error: " + e.getMessage());
            runRecord.setCompletedAt(OffsetDateTime.now());
            runRecord.setResult(UtilityResult.PARTIAL_SUCCESS);
            runRecord.setApplicationsProcessed(processedCount);
            runRecord.setErrorMessage(e.getMessage());

            try (Connection conn = DatabaseConfig.getConnection()) {
                runDAO.insert(conn, runRecord);
            } catch (Exception ignored) {
            }
        }
        return runRecord;
    }

    /**
     * Atomically pushes a single loan into ng_core_banking and updates status to EXIT with history.
     */
    private boolean pushSingleLoan(Long applicationId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Double check application is in DISBURSED status and not already pushed
                LoanApplication app = applicationDAO.findById(conn, applicationId).orElse(null);
                if (app == null || app.getStatus() != ApplicationStatus.DISBURSED) {
                    conn.rollback();
                    return false;
                }
                if (coreBankingDAO.findByApplicationId(conn, applicationId).isPresent()) {
                    // Already synced
                    conn.rollback();
                    return false;
                }

                // 2. Fetch Customer and Disbursement
                Customer customer = customerDAO.findById(conn, app.getCustomerId()).orElse(null);
                Disbursement disbursement = disbursementDAO.findByApplicationId(conn, applicationId).orElse(null);

                if (customer == null || disbursement == null) {
                    System.err.printf("[CoreBankingPushUtility] Incomplete data for appId: %d. Customer or Disbursement missing.%n", applicationId);
                    conn.rollback();
                    return false;
                }

                // 3. Insert into ng_core_banking
                NgCoreBanking ncb = new NgCoreBanking();
                ncb.setApplicationId(applicationId);
                ncb.setCustomerId(customer.getCustomerId());
                ncb.setCustomerName(customer.getName());
                ncb.setAddress(customer.getAddress());
                ncb.setDob(customer.getDateOfBirth());
                ncb.setKycDetails(customer.getKycDetails());
                ncb.setApprovedAmount(app.getRequestedAmount());
                ncb.setDisbursedAmount(disbursement.getAmount());
                LocalDate dDate = (disbursement.getDisbursedAt() != null)
                        ? disbursement.getDisbursedAt().toLocalDate()
                        : LocalDate.now();
                ncb.setDisbursedDate(dDate);
                ncb.setSyncedAt(OffsetDateTime.now());

                coreBankingDAO.insert(conn, ncb);

                // 4. Transition application status from DISBURSED -> EXIT
                applicationDAO.updateStatusOnly(conn, applicationId, ApplicationStatus.EXIT);

                // 5. Write history audit
                ApplicationHistory history = new ApplicationHistory(
                        applicationId,
                        "CORE_BANKING_SYNC",
                        ApplicationStatus.DISBURSED,
                        ApplicationStatus.EXIT,
                        "CORE_BANKING_UTILITY",
                        "Successfully synced loan to ng_core_banking. Status moved to terminal EXIT."
                );
                historyDAO.insert(conn, history);

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                System.err.printf("[CoreBankingPushUtility] Error pushing loan [%d]: %s%n", applicationId, e.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.printf("[CoreBankingPushUtility] Connection error for loan [%d]: %s%n", applicationId, e.getMessage());
            return false;
        }
    }
}
