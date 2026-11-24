package com.los.service;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.dao.ApplicationHistoryDAO;
import com.los.dao.CustomerDAO;
import com.los.dao.DisbursementDAO;
import com.los.dao.LoanApplicationDAO;
import com.los.model.ApplicationHistory;
import com.los.model.ApplicationStatus;
import com.los.model.Customer;
import com.los.model.DataAccessException;
import com.los.model.Disbursement;
import com.los.model.DuplicateEntityException;
import com.los.model.EligibilityResult;
import com.los.model.EntityNotFoundException;
import com.los.model.InvalidStateTransitionException;
import com.los.model.LoanApplication;
import com.los.model.ProductType;
import com.los.model.ValidationException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Service orchestrating the complete Loan Origination Lifecycle.
 * Implements strict ACID transactions, finite state machine transitions, and audit trails.
 */
public class LoanApplicationService {

    private final LoanApplicationDAO applicationDAO;
    private final CustomerDAO customerDAO;
    private final DisbursementDAO disbursementDAO;
    private final ApplicationHistoryDAO historyDAO;
    private final IdGeneratorService idGeneratorService;
    private final FinancialCalculationService financialService;
    private final EligibilityRuleEngine ruleEngine;
    private final AppConfig config;

    public LoanApplicationService() {
        this.applicationDAO = new LoanApplicationDAO();
        this.customerDAO = new CustomerDAO();
        this.disbursementDAO = new DisbursementDAO();
        this.historyDAO = new ApplicationHistoryDAO();
        this.idGeneratorService = new IdGeneratorService();
        this.financialService = new FinancialCalculationService();
        this.ruleEngine = new EligibilityRuleEngine();
        this.config = AppConfig.getInstance();
    }

    public LoanApplicationService(LoanApplicationDAO applicationDAO, CustomerDAO customerDAO,
                                 DisbursementDAO disbursementDAO, ApplicationHistoryDAO historyDAO,
                                 IdGeneratorService idGeneratorService, FinancialCalculationService financialService,
                                 EligibilityRuleEngine ruleEngine, AppConfig config) {
        this.applicationDAO = applicationDAO;
        this.customerDAO = customerDAO;
        this.disbursementDAO = disbursementDAO;
        this.historyDAO = historyDAO;
        this.idGeneratorService = idGeneratorService;
        this.financialService = financialService;
        this.ruleEngine = ruleEngine;
        this.config = config;
    }

    /**
     * Creates a new Loan Application in DRAFT status.
     */
    public LoanApplication createDraft(LoanApplication app, String performedBy) {
        validateDraftInputs(app);
        String user = (performedBy != null && !performedBy.trim().isEmpty()) ? performedBy.trim() : "SYSTEM";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Verify Customer exists
                Customer customer = customerDAO.findById(conn, app.getCustomerId())
                        .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + app.getCustomerId()));

                // 2. Enforce Max 1 Active Application per (customer_id, product)
                if (applicationDAO.hasActiveApplication(conn, app.getCustomerId(), app.getProduct())) {
                    throw new DuplicateEntityException(String.format(
                            "Customer [%s] already has an active application in DRAFT, UNDER_REVIEW, or CREDIT_APPROVAL for %s loan.",
                            app.getCustomerId(), app.getProduct().name()));
                }

                // 3. Set Interest rate & tentative calculations
                BigDecimal rate = (app.getProduct() == ProductType.PERSONAL)
                        ? config.getPersonalInterestRate()
                        : config.getEducationInterestRate();
                app.setAnnualInterestRate(rate);

                BigDecimal emi = financialService.calculateEMI(app.getRequestedAmount(), rate, app.getTenureMonths());
                app.setProposedEmi(emi);

                BigDecimal foir = financialService.calculateFOIR(app.getExistingMonthlyObligations(), emi, app.getMonthlyIncome());
                app.setFoirPercentage(foir);

                // 4. Generate Application Number
                LocalDate bDate = config.getBusinessDate();
                String appNumber = idGeneratorService.generateApplicationNumber(conn, bDate);
                app.setApplicationNumber(appNumber);
                app.setStatus(ApplicationStatus.DRAFT);
                app.setEligible(false);
                app.setCreatedAt(OffsetDateTime.now());

                // 5. Persist Application
                Long appId = applicationDAO.insert(conn, app);

                // 6. Audit Trail History
                ApplicationHistory history = new ApplicationHistory(
                        appId,
                        "CREATE_DRAFT",
                        null,
                        ApplicationStatus.DRAFT,
                        user,
                        "Draft loan application created successfully."
                );
                historyDAO.insert(conn, history);

                conn.commit();
                return app;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (DuplicateEntityException | EntityNotFoundException | ValidationException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create draft application: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing application draft. Strictly permitted ONLY while status is DRAFT.
     */
    public LoanApplication updateDraft(LoanApplication app, String performedBy) {
        if (app.getApplicationId() == null) {
            throw new ValidationException("Application ID is required for updating draft.");
        }
        validateDraftInputs(app);
        String user = (performedBy != null && !performedBy.trim().isEmpty()) ? performedBy.trim() : "SYSTEM";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LoanApplication existing = applicationDAO.findById(conn, app.getApplicationId())
                        .orElseThrow(() -> new EntityNotFoundException("Application not found: " + app.getApplicationId()));

                if (existing.getStatus() != ApplicationStatus.DRAFT) {
                    throw new InvalidStateTransitionException("Modification is strictly allowed only in DRAFT status. Current status: " + existing.getStatus());
                }

                // If product changed, re-check active application constraint
                if (existing.getProduct() != app.getProduct()) {
                    if (applicationDAO.hasActiveApplication(conn, existing.getCustomerId(), app.getProduct())) {
                        throw new DuplicateEntityException(String.format(
                                "Customer already has an active %s application.", app.getProduct().name()));
                    }
                }

                BigDecimal rate = (app.getProduct() == ProductType.PERSONAL)
                        ? config.getPersonalInterestRate()
                        : config.getEducationInterestRate();
                app.setAnnualInterestRate(rate);

                BigDecimal emi = financialService.calculateEMI(app.getRequestedAmount(), rate, app.getTenureMonths());
                app.setProposedEmi(emi);

                BigDecimal foir = financialService.calculateFOIR(app.getExistingMonthlyObligations(), emi, app.getMonthlyIncome());
                app.setFoirPercentage(foir);

                applicationDAO.updateDraft(conn, app);

                ApplicationHistory history = new ApplicationHistory(
                        app.getApplicationId(),
                        "UPDATE_DRAFT",
                        ApplicationStatus.DRAFT,
                        ApplicationStatus.DRAFT,
                        user,
                        "Application parameters updated in draft state."
                );
                historyDAO.insert(conn, history);

                conn.commit();
                return getApplicationById(app.getApplicationId());
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (InvalidStateTransitionException | EntityNotFoundException | DuplicateEntityException | ValidationException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update draft application: " + e.getMessage(), e);
        }
    }

    /**
     * Submits a DRAFT application for underwriting.
     * Evaluates all policy rules:
     *  - If eligible -> Transitions to UNDER_REVIEW
     *  - If ineligible -> Transitions to REJECTED (storing all failure reasons)
     */
    public LoanApplication submitApplication(Long applicationId, String performedBy) {
        String user = (performedBy != null && !performedBy.trim().isEmpty()) ? performedBy.trim() : "SYSTEM";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LoanApplication app = applicationDAO.findById(conn, applicationId)
                        .orElseThrow(() -> new EntityNotFoundException("Application not found: " + applicationId));

                if (app.getStatus() != ApplicationStatus.DRAFT) {
                    throw new InvalidStateTransitionException(
                            String.format("Application can only be submitted from DRAFT status. Current status: %s", app.getStatus()));
                }

                Customer customer = customerDAO.findById(conn, app.getCustomerId())
                        .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + app.getCustomerId()));

                LocalDate businessDate = config.getBusinessDate();
                EligibilityResult result = ruleEngine.evaluate(customer, app, businessDate);

                OffsetDateTime now = OffsetDateTime.now();
                ApplicationStatus newStatus;
                String remarks;

                if (result.isEligible()) {
                    newStatus = ApplicationStatus.UNDER_REVIEW;
                    remarks = "Application submitted successfully and moved to UNDER_REVIEW.";
                    applicationDAO.updateSubmissionStatus(conn, applicationId, newStatus, true,
                            result.getFormattedReasons(), now, null, null);
                } else {
                    newStatus = ApplicationStatus.REJECTED;
                    remarks = "Application submitted and REJECTED due to policy criteria failures: " + result.getFormattedReasons();
                    applicationDAO.updateSubmissionStatus(conn, applicationId, newStatus, false,
                            result.getFormattedReasons(), now, now, result.getFormattedReasons());
                }

                ApplicationHistory history = new ApplicationHistory(
                        applicationId,
                        "SUBMIT",
                        ApplicationStatus.DRAFT,
                        newStatus,
                        user,
                        remarks
                );
                historyDAO.insert(conn, history);

                conn.commit();
                return applicationDAO.findById(conn, applicationId).orElse(app);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (InvalidStateTransitionException | EntityNotFoundException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to submit loan application: " + e.getMessage(), e);
        }
    }

    /**
     * Credit Reviewer Decision:
     *  - If approve = true -> Transitions to CREDIT_APPROVAL (retaining requested amount & tenure)
     *  - If approve = false -> Transitions to REJECTED (requires non-blank rejection reason)
     */
    public LoanApplication reviewApplication(Long applicationId, boolean approve, String reviewerCode, String decisionReason, String performedBy) {
        String reviewer = (reviewerCode != null && !reviewerCode.trim().isEmpty()) ? reviewerCode.trim() : "CREDIT_OFFICER";
        String user = (performedBy != null && !performedBy.trim().isEmpty()) ? performedBy.trim() : reviewer;

        if (!approve && (decisionReason == null || decisionReason.trim().isEmpty())) {
            throw new ValidationException("A non-blank rejection reason is mandatory when rejecting an application.");
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LoanApplication app = applicationDAO.findById(conn, applicationId)
                        .orElseThrow(() -> new EntityNotFoundException("Application not found: " + applicationId));

                if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                    throw new InvalidStateTransitionException(
                            String.format("Review decision requires UNDER_REVIEW status. Current status: %s", app.getStatus()));
                }

                OffsetDateTime now = OffsetDateTime.now();
                ApplicationStatus newStatus = approve ? ApplicationStatus.CREDIT_APPROVAL : ApplicationStatus.REJECTED;
                String reason = approve
                        ? (decisionReason != null && !decisionReason.trim().isEmpty() ? decisionReason.trim() : "Approved by Credit Committee")
                        : decisionReason.trim();

                applicationDAO.updateReviewStatus(conn, applicationId, newStatus, reviewer, reason, now);

                ApplicationHistory history = new ApplicationHistory(
                        applicationId,
                        approve ? "REVIEW_APPROVE" : "REVIEW_REJECT",
                        ApplicationStatus.UNDER_REVIEW,
                        newStatus,
                        user,
                        "Decision: " + (approve ? "APPROVED" : "REJECTED") + ". Reason: " + reason
                );
                historyDAO.insert(conn, history);

                conn.commit();
                return applicationDAO.findById(conn, applicationId).orElse(app);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (InvalidStateTransitionException | EntityNotFoundException | ValidationException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record credit review decision: " + e.getMessage(), e);
        }
    }

    /**
     * Executes single payout disbursement for approved loan.
     * Transitions status: CREDIT_APPROVAL -> DISBURSED
     */
    public Disbursement disburseLoan(Long applicationId, String reference, String performedBy) {
        String user = (performedBy != null && !performedBy.trim().isEmpty()) ? performedBy.trim() : "DISBURSEMENT_OFFICER";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LoanApplication app = applicationDAO.findById(conn, applicationId)
                        .orElseThrow(() -> new EntityNotFoundException("Application not found: " + applicationId));

                if (app.getStatus() != ApplicationStatus.CREDIT_APPROVAL) {
                    throw new InvalidStateTransitionException(
                            String.format("Disbursement requires CREDIT_APPROVAL status. Current status: %s", app.getStatus()));
                }

                // Generate reference if not provided
                String ref = (reference != null && !reference.trim().isEmpty())
                        ? reference.trim()
                        : idGeneratorService.generateDisbursementReference(conn, config.getBusinessDate());

                if (disbursementDAO.existsByReference(conn, ref)) {
                    throw new DuplicateEntityException("Disbursement reference already exists: " + ref);
                }

                OffsetDateTime now = OffsetDateTime.now();
                Disbursement disbursement = new Disbursement(
                        applicationId,
                        ref,
                        app.getRequestedAmount(),
                        now
                );
                disbursementDAO.insert(conn, disbursement);

                // Update application status to DISBURSED
                applicationDAO.updateStatusOnly(conn, applicationId, ApplicationStatus.DISBURSED);

                ApplicationHistory history = new ApplicationHistory(
                        applicationId,
                        "DISBURSE",
                        ApplicationStatus.CREDIT_APPROVAL,
                        ApplicationStatus.DISBURSED,
                        user,
                        "Disbursed full approved amount of " + app.getRequestedAmount() + " with reference " + ref
                );
                historyDAO.insert(conn, history);

                conn.commit();
                return disbursement;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (InvalidStateTransitionException | EntityNotFoundException | DuplicateEntityException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to execute loan disbursement: " + e.getMessage(), e);
        }
    }

    public LoanApplication getApplicationById(Long applicationId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return applicationDAO.findById(conn, applicationId)
                    .orElseThrow(() -> new EntityNotFoundException("Application not found with ID: " + applicationId));
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching application by ID: " + e.getMessage(), e);
        }
    }

    public LoanApplication getApplicationByNumber(String appNumber) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return applicationDAO.findByApplicationNumber(conn, appNumber)
                    .orElseThrow(() -> new EntityNotFoundException("Application not found with number: " + appNumber));
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching application by number: " + e.getMessage(), e);
        }
    }

    public List<LoanApplication> getApplicationsByCustomer(String customerId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return applicationDAO.findByCustomerId(conn, customerId);
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching applications for customer: " + e.getMessage(), e);
        }
    }

    public List<ApplicationHistory> getApplicationHistory(Long applicationId) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return historyDAO.findByApplicationId(conn, applicationId);
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching application history: " + e.getMessage(), e);
        }
    }

    public List<LoanApplication> getAllApplications() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return applicationDAO.findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Error fetching all applications: " + e.getMessage(), e);
        }
    }

    public List<LoanApplication> filterApplications(ProductType product, ApplicationStatus status) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            return applicationDAO.filterApplications(conn, product, status);
        } catch (SQLException e) {
            throw new DataAccessException("Error filtering applications: " + e.getMessage(), e);
        }
    }

    private void validateDraftInputs(LoanApplication app) {
        List<String> errors = new ArrayList<>();
        if (app == null) {
            throw new ValidationException("Application object cannot be null.");
        }
        if (app.getCustomerId() == null || app.getCustomerId().trim().isEmpty()) {
            errors.add("Customer ID is required.");
        }
        if (app.getProduct() == null) {
            errors.add("Product type (PERSONAL or EDUCATION) is required.");
        }
        if (app.getBranchCode() == null || app.getBranchCode().trim().isEmpty()) {
            errors.add("Branch code is required.");
        }
        if (app.getBranchName() == null || app.getBranchName().trim().isEmpty()) {
            errors.add("Branch name is required.");
        }
        if (app.getRequestedAmount() == null || app.getRequestedAmount().signum() <= 0) {
            errors.add("Requested amount must be strictly positive.");
        }
        if (app.getTenureMonths() <= 0) {
            errors.add("Tenure in months must be positive.");
        }
        if (app.getMonthlyIncome() == null || app.getMonthlyIncome().signum() <= 0) {
            errors.add("Monthly net income must be strictly positive.");
        }
        if (app.getExistingMonthlyObligations() == null) {
            app.setExistingMonthlyObligations(BigDecimal.ZERO);
        } else if (app.getExistingMonthlyObligations().signum() < 0) {
            errors.add("Existing monthly obligations cannot be negative.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
