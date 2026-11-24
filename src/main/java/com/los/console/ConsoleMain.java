package com.los.console;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.config.IndianCurrencyFormatter;
import com.los.model.ApplicationHistory;
import com.los.model.ApplicationRegisterItem;
import com.los.model.ApplicationStatus;
import com.los.model.Customer;
import com.los.model.CustomerWithoutDisbursementItem;
import com.los.model.Disbursement;
import com.los.model.DisbursementSummaryItem;
import com.los.model.LoanApplication;
import com.los.model.OverdueReviewItem;
import com.los.model.PipelineSummaryItem;
import com.los.model.ProductType;
import com.los.model.RepeatApplicantItem;
import com.los.model.UserContext;
import com.los.model.UtilityRun;
import com.los.service.CustomerService;
import com.los.service.FinancialCalculationService;
import com.los.service.LoanApplicationService;
import com.los.service.ReportService;
import com.los.utility.BackgroundPipelineScheduler;
import com.los.utility.CoreBankingPushUtility;
import com.los.utility.OverdueReviewMonitor;
import com.los.web.LosWebServer;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Enterprise Production Console Entrypoint for Mini Loan Origination System (LOS).
 * Provides a comprehensive, menu-driven CLI interface with automated background scheduling.
 */
public class ConsoleMain {

    private final CustomerService customerService;
    private final LoanApplicationService applicationService;
    private final ReportService reportService;
    private final FinancialCalculationService financialService;
    private final OverdueReviewMonitor overdueMonitor;
    private final CoreBankingPushUtility coreBankingPushUtility;
    private final BackgroundPipelineScheduler pipelineScheduler;
    private final AppConfig config;
    private LosWebServer webServer;
    private final Scanner scanner;
    private UserContext currentUser;

    public ConsoleMain() {
        this.customerService = new CustomerService();
        this.applicationService = new LoanApplicationService();
        this.reportService = new ReportService();
        this.financialService = new FinancialCalculationService();
        this.overdueMonitor = new OverdueReviewMonitor();
        this.coreBankingPushUtility = new CoreBankingPushUtility();
        this.pipelineScheduler = new BackgroundPipelineScheduler();
        this.config = AppConfig.getInstance();
        this.scanner = new Scanner(System.in);
        this.currentUser = new UserContext("admin_super", com.los.model.UserRole.ADMIN);
    }

    public static void main(String[] args) {
        ConsoleMain console = new ConsoleMain();
        console.start();
    }

    public void start() {
        printBanner();

        // 1. Check and initialize Database connection
        boolean connected = DatabaseConfig.testConnection();
        if (connected) {
            System.out.println("  [DB] Connection to PostgreSQL (Supabase) verified successfully.");
            try {
                DatabaseConfig.initializeDatabase();
            } catch (Exception e) {
                System.out.println("  [DB] Note: Auto-init schema: " + e.getMessage());
            }
        } else {
            System.err.println("  [DB WARNING] Could not connect to PostgreSQL. Please check config.properties or DB_URL.");
        }

        // 2. Start Background Pipelines (ScheduledExecutorService)
        pipelineScheduler.start();

        // 3. Main Console Interactive Loop
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Select Option (1-7): ").trim();
            switch (choice) {
                case "1" -> handleCustomerMenu();
                case "2" -> handleLoanApplicationMenu();
                case "3" -> handleReportsMenu();
                case "4" -> handleUtilitiesMenu();
                case "5" -> handleSystemMenu();
                case "6" -> handleSwitchRoleMenu();
                case "7" -> {
                    System.out.println("\nShutting down Mini LOS...");
                    pipelineScheduler.stop();
                    if (webServer != null) {
                        webServer.stop();
                    }
                    running = false;
                    System.out.println("Goodbye!");
                }
                default -> System.out.println("Invalid selection. Please choose an option from 1 to 7.");
            }
        }
    }

    private void printBanner() {
        System.out.println("================================================================================");
        System.out.println("   MINI LOAN ORIGINATION SYSTEM (LOS) - ENTERPRISE CORE v1.0                    ");
        System.out.println("   Java 21 | Raw JDBC | PostgreSQL | Dynamic Supabase & RBAC Enabled            ");
        System.out.println("   Configured Business Date: " + config.getBusinessDate());
        System.out.println("   Current Security Context: " + currentUser.getUsername() + " (" + currentUser.getRole() + ")");
        System.out.println("================================================================================");
    }

    private void printMainMenu() {
        System.out.println("\n----------------------------- MAIN MENU -----------------------------");
        System.out.println("  Active User: " + currentUser.getUsername() + " | Role: " + currentUser.getRole());
        System.out.println("  1. Customer Management (Onboarding & Lookups)");
        System.out.println("  2. Loan Origination Lifecycle (Draft -> Submit -> Review -> Disburse)");
        System.out.println("  3. Parameterized Enterprise Reports (6 SQL Analytics Queries)");
        System.out.println("  4. Background Jobs & CSV Utilities (Overdue Monitor & Core Banking)");
        System.out.println("  5. Web Server & Database Administration");
        System.out.println("  6. Switch Active User Role (RBAC)");
        System.out.println("  7. Exit");
        System.out.println("---------------------------------------------------------------------");
    }

    // -------------------------------------------------------------------------
    // 1. CUSTOMER MANAGEMENT
    // -------------------------------------------------------------------------
    private void handleCustomerMenu() {
        System.out.println("\n--- CUSTOMER MANAGEMENT ---");
        System.out.println("  1. Register New Customer (CUST-XXXXXX)");
        System.out.println("  2. Search Customer by ID");
        System.out.println("  3. Search Customer by Mobile");
        System.out.println("  4. List All Customers");
        System.out.println("  5. Return to Main Menu");

        String choice = prompt("Choose (1-5): ").trim();
        switch (choice) {
            case "1" -> registerCustomer();
            case "2" -> searchCustomerById();
            case "3" -> searchCustomerByMobile();
            case "4" -> listAllCustomers();
            case "5" -> { /* return */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    private void registerCustomer() {
        if (!currentUser.hasPermission(com.los.model.Permission.CUSTOMER_CREATE)) {
            System.err.println("[SECURITY] Access Denied: Current role [" + currentUser.getRole() + "] does not have permission to register customers.");
            return;
        }
        System.out.println("\n[Customer Registration]");
        String name = prompt("Enter Full Name: ");
        String mobile = prompt("Enter 10-digit Mobile Number: ");
        String dobStr = prompt("Enter Date of Birth (YYYY-MM-DD): ");
        LocalDate dob;
        try {
            dob = LocalDate.parse(dobStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date format. Expected YYYY-MM-DD.");
            return;
        }
        String address = prompt("Enter Residential Address: ");
        String kyc = prompt("Enter KYC Details (e.g. PAN / Aadhaar): ");

        Customer c = new Customer();
        c.setName(name);
        c.setMobile(mobile);
        c.setDateOfBirth(dob);
        c.setAddress(address);
        c.setKycDetails(kyc);

        try {
            Customer created = customerService.registerCustomer(c);
            System.out.println("\n>>> SUCCESS: Customer registered!");
            System.out.println("    Customer ID: " + created.getCustomerId());
            System.out.println("    Name:        " + created.getName());
            System.out.println("    Mobile:      " + created.getMobile());
            System.out.println("    DOB:         " + created.getDateOfBirth());
        } catch (Exception e) {
            System.err.println("\n[ERROR] Registration failed: " + e.getMessage());
        }
    }

    private void searchCustomerById() {
        String id = prompt("Enter Customer ID (e.g. CUST-XXXXXX): ");
        try {
            Customer c = customerService.getCustomerById(id);
            printCustomerDetails(c);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    private void searchCustomerByMobile() {
        String mobile = prompt("Enter 10-digit Mobile Number: ");
        try {
            Customer c = customerService.getCustomerByMobile(mobile);
            printCustomerDetails(c);
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    private void listAllCustomers() {
        try {
            List<Customer> list = customerService.getAllCustomers();
            System.out.println("\nRegistered Customers (" + list.size() + " total):");
            System.out.printf("%-15s %-22s %-12s %-12s %-25s%n", "Customer ID", "Name", "Mobile", "DOB", "KYC");
            System.out.println("--------------------------------------------------------------------------------");
            for (Customer c : list) {
                System.out.printf("%-15s %-22s %-12s %-12s %-25s%n",
                        c.getCustomerId(), c.getName(), c.getMobile(), c.getDateOfBirth(), c.getKycDetails());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    private void printCustomerDetails(Customer c) {
        System.out.println("\nCustomer Details:");
        System.out.println("  ID:      " + c.getCustomerId());
        System.out.println("  Name:    " + c.getName());
        System.out.println("  Mobile:  " + c.getMobile());
        System.out.println("  DOB:     " + c.getDateOfBirth());
        System.out.println("  Address: " + c.getAddress());
        System.out.println("  KYC:     " + c.getKycDetails());
    }

    // -------------------------------------------------------------------------
    // 2. LOAN ORIGINATION WORKFLOW
    // -------------------------------------------------------------------------
    private void handleLoanApplicationMenu() {
        System.out.println("\n--- LOAN ORIGINATION LIFECYCLE ---");
        System.out.println("  1. Create Loan Application Draft (DRAFT)");
        System.out.println("  2. Edit Existing Draft (DRAFT only)");
        System.out.println("  3. Submit Application for Underwriting (DRAFT -> UNDER_REVIEW / REJECTED)");
        System.out.println("  4. Credit Officer Review (UNDER_REVIEW -> CREDIT_APPROVAL / REJECTED)");
        System.out.println("  5. Disburse Approved Loan (CREDIT_APPROVAL -> DISBURSED)");
        System.out.println("  6. View Application Details & Complete Audit Trail");
        System.out.println("  7. List All Applications in Pipeline");
        System.out.println("  8. Return to Main Menu");

        String choice = prompt("Choose (1-8): ").trim();
        switch (choice) {
            case "1" -> createDraftApplication();
            case "2" -> editDraftApplication();
            case "3" -> submitApplicationForUnderwriting();
            case "4" -> reviewApplication();
            case "5" -> disburseApplication();
            case "6" -> viewApplicationDetails();
            case "7" -> listApplications();
            case "8" -> { /* return */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    private void createDraftApplication() {
        if (!currentUser.hasPermission(com.los.model.Permission.APPLICATION_DRAFT_CREATE)) {
            System.err.println("[SECURITY] Access Denied: Role [" + currentUser.getRole() + "] cannot create application drafts.");
            return;
        }
        System.out.println("\n[Create Application Draft]");
        String custId = prompt("Enter Customer ID: ");
        System.out.println("Available Products: 1. PERSONAL (12% p.a.) | 2. EDUCATION (10% p.a.)");
        String prodChoice = prompt("Choose Product (1 or 2): ").trim();
        ProductType prod = prodChoice.equals("2") ? ProductType.EDUCATION : ProductType.PERSONAL;

        String amountStr = prompt("Enter Requested Amount (INR): ");
        String tenureStr = prompt("Enter Tenure in Months: ");
        String incomeStr = prompt("Enter Monthly Net Income (INR): ");
        String obligStr = prompt("Enter Existing Monthly Obligations (INR, 0 if none): ");
        String branchCode = prompt("Enter Branch Code (e.g. BR001): ");
        String branchName = prompt("Enter Branch Name (e.g. Mumbai Main): ");

        try {
            LoanApplication app = new LoanApplication();
            app.setCustomerId(custId.trim());
            app.setProduct(prod);
            app.setRequestedAmount(new BigDecimal(amountStr.trim()));
            app.setTenureMonths(Integer.parseInt(tenureStr.trim()));
            app.setMonthlyIncome(new BigDecimal(incomeStr.trim()));
            app.setExistingMonthlyObligations(new BigDecimal(obligStr.trim().isEmpty() ? "0" : obligStr.trim()));
            app.setBranchCode(branchCode.trim());
            app.setBranchName(branchName.trim());

            LoanApplication created = applicationService.createDraft(app, "CONSOLE_USER");
            System.out.println("\n>>> SUCCESS: Draft application created!");
            System.out.println("    Application ID:     " + created.getApplicationId());
            System.out.println("    Application Number: " + created.getApplicationNumber());
            System.out.println("    Product:            " + created.getProduct());
            System.out.println("    Requested Amount:   " + IndianCurrencyFormatter.formatWithPrefix(created.getRequestedAmount()));
            System.out.println("    Proposed EMI:       " + IndianCurrencyFormatter.formatWithPrefix(created.getProposedEmi()));
            System.out.println("    Estimated FOIR:     " + created.getFoirPercentage() + "%");
            System.out.println("    Status:             " + created.getStatus());
        } catch (Exception e) {
            System.err.println("\n[ERROR] Draft creation failed: " + e.getMessage());
        }
    }

    private void editDraftApplication() {
        System.out.println("\n[Edit Application Draft]");
        String idStr = prompt("Enter Application ID to edit: ");
        try {
            long id = Long.parseLong(idStr.trim());
            LoanApplication app = applicationService.getApplicationById(id);
            if (app.getStatus() != ApplicationStatus.DRAFT) {
                System.err.println("Cannot edit: Application is in " + app.getStatus() + " status. Editing is only permitted in DRAFT.");
                return;
            }

            System.out.println("Editing Application: " + app.getApplicationNumber());
            String amountStr = prompt("New Requested Amount [" + app.getRequestedAmount() + "]: ");
            if (!amountStr.trim().isEmpty()) {
                app.setRequestedAmount(new BigDecimal(amountStr.trim()));
            }
            String tenureStr = prompt("New Tenure Months [" + app.getTenureMonths() + "]: ");
            if (!tenureStr.trim().isEmpty()) {
                app.setTenureMonths(Integer.parseInt(tenureStr.trim()));
            }
            String incomeStr = prompt("New Monthly Income [" + app.getMonthlyIncome() + "]: ");
            if (!incomeStr.trim().isEmpty()) {
                app.setMonthlyIncome(new BigDecimal(incomeStr.trim()));
            }
            String obligStr = prompt("New Obligations [" + app.getExistingMonthlyObligations() + "]: ");
            if (!obligStr.trim().isEmpty()) {
                app.setExistingMonthlyObligations(new BigDecimal(obligStr.trim()));
            }

            LoanApplication updated = applicationService.updateDraft(app, "CONSOLE_USER");
            System.out.println("\n>>> SUCCESS: Draft updated!");
            System.out.println("    Recalculated Proposed EMI: " + IndianCurrencyFormatter.formatWithPrefix(updated.getProposedEmi()));
            System.out.println("    Recalculated FOIR:         " + updated.getFoirPercentage() + "%");
        } catch (Exception e) {
            System.err.println("\n[ERROR] Edit failed: " + e.getMessage());
        }
    }

    private void submitApplicationForUnderwriting() {
        System.out.println("\n[Submit Application for Underwriting]");
        String idStr = prompt("Enter Application ID to submit: ");
        try {
            long id = Long.parseLong(idStr.trim());
            LoanApplication submitted = applicationService.submitApplication(id, "CONSOLE_USER");

            System.out.println("\n>>> UNDERWRITING EVALUATION RESULT:");
            System.out.println("    Application Number: " + submitted.getApplicationNumber());
            System.out.println("    Resulting Status:   " + submitted.getStatus());
            System.out.println("    Eligible Flag:      " + (submitted.isEligible() ? "PASS (True)" : "FAIL (False)"));
            System.out.println("    Policy Evaluation:  " + submitted.getEligibilityReasons());
        } catch (Exception e) {
            System.err.println("\n[ERROR] Submission failed: " + e.getMessage());
        }
    }

    private void reviewApplication() {
        if (!currentUser.hasPermission(com.los.model.Permission.APPLICATION_REVIEW)) {
            System.err.println("[SECURITY] Access Denied: Role [" + currentUser.getRole() + "] cannot review or approve/reject applications.");
            return;
        }
        System.out.println("\n[Credit Officer Review Decision]");
        String idStr = prompt("Enter Application ID under review: ");
        try {
            long id = Long.parseLong(idStr.trim());
            LoanApplication app = applicationService.getApplicationById(id);
            if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) {
                System.err.println("Cannot review: Application is currently in " + app.getStatus() + " status (expected UNDER_REVIEW).");
                return;
            }

            System.out.println("Application: " + app.getApplicationNumber() + " | Amount: " + IndianCurrencyFormatter.formatWithPrefix(app.getRequestedAmount()));
            String dec = prompt("Enter Decision (1 = APPROVE, 2 = REJECT): ").trim();
            boolean approve = dec.equals("1");

            String reviewer = prompt("Enter Reviewer Code [CR-OFFICER-01]: ").trim();
            if (reviewer.isEmpty()) reviewer = "CR-OFFICER-01";

            String reason = prompt("Enter Decision Reason / Remarks (Mandatory if Rejecting): ").trim();

            LoanApplication reviewed = applicationService.reviewApplication(id, approve, reviewer, reason, reviewer);
            System.out.println("\n>>> SUCCESS: Review Decision Recorded!");
            System.out.println("    New Status:      " + reviewed.getStatus());
            System.out.println("    Reviewer:        " + reviewed.getReviewerCode());
            System.out.println("    Decision Reason: " + reviewed.getDecisionReason());
        } catch (Exception e) {
            System.err.println("\n[ERROR] Review failed: " + e.getMessage());
        }
    }

    private void disburseApplication() {
        if (!currentUser.hasPermission(com.los.model.Permission.APPLICATION_DISBURSE)) {
            System.err.println("[SECURITY] Access Denied: Role [" + currentUser.getRole() + "] cannot execute disbursements.");
            return;
        }
        System.out.println("\n[Loan Disbursement]");
        String idStr = prompt("Enter Application ID for payout: ");
        try {
            long id = Long.parseLong(idStr.trim());
            String ref = prompt("Enter Unique Reference (Leave blank to auto-generate): ").trim();

            Disbursement d = applicationService.disburseLoan(id, ref.isEmpty() ? null : ref, "TREASURY_OFFICER");
            System.out.println("\n>>> SUCCESS: Loan Disbursed!");
            System.out.println("    Disbursement ID:  " + d.getDisbursementId());
            System.out.println("    Reference:        " + d.getReference());
            System.out.println("    Disbursed Amount: " + IndianCurrencyFormatter.formatWithPrefix(d.getAmount()));
            System.out.println("    Timestamp:        " + d.getDisbursedAt());
            System.out.println("    (Ready for Next-Gen Core Banking Push background utility)");
        } catch (Exception e) {
            System.err.println("\n[ERROR] Disbursement failed: " + e.getMessage());
        }
    }

    private void viewApplicationDetails() {
        String idStr = prompt("Enter Application ID: ");
        try {
            long id = Long.parseLong(idStr.trim());
            LoanApplication app = applicationService.getApplicationById(id);
            System.out.println("\n================ Application Details ================");
            System.out.println("  ID:                 " + app.getApplicationId());
            System.out.println("  Application Number: " + app.getApplicationNumber());
            System.out.println("  Customer ID:        " + app.getCustomerId());
            System.out.println("  Product:            " + app.getProduct());
            System.out.println("  Branch:             " + app.getBranchCode() + " - " + app.getBranchName());
            System.out.println("  Requested Amount:   " + IndianCurrencyFormatter.formatWithPrefix(app.getRequestedAmount()));
            System.out.println("  Tenure Months:      " + app.getTenureMonths());
            System.out.println("  Monthly Income:     " + IndianCurrencyFormatter.formatWithPrefix(app.getMonthlyIncome()));
            System.out.println("  Existing Oblig:     " + IndianCurrencyFormatter.formatWithPrefix(app.getExistingMonthlyObligations()));
            System.out.println("  Annual Rate:        " + app.getAnnualInterestRate() + "%");
            System.out.println("  Proposed EMI:       " + IndianCurrencyFormatter.formatWithPrefix(app.getProposedEmi()));
            System.out.println("  FOIR:               " + app.getFoirPercentage() + "%");
            System.out.println("  Status:             " + app.getStatus());
            System.out.println("  Eligibility Notes:  " + app.getEligibilityReasons());
            System.out.println("  Submitted At:       " + app.getSubmittedAt());
            System.out.println("  Decided At:         " + app.getDecidedAt());
            System.out.println("  Decision Reason:    " + app.getDecisionReason());

            List<ApplicationHistory> history = applicationService.getApplicationHistory(id);
            System.out.println("\n------------------- Audit Trail -------------------");
            for (ApplicationHistory h : history) {
                System.out.printf("[%s] %-15s | Old: %-13s -> New: %-13s | By: %-15s | Note: %s%n",
                        h.getPerformedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        h.getAction(),
                        h.getOldStatus() != null ? h.getOldStatus().name() : "NONE",
                        h.getNewStatus().name(),
                        h.getPerformedBy(),
                        h.getRemarks());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    private void listApplications() {
        try {
            List<LoanApplication> list = applicationService.getAllApplications();
            System.out.println("\nApplication Register (" + list.size() + " total):");
            System.out.printf("%-6s %-16s %-12s %-10s %-15s %-15s %-15s%n",
                    "ID", "App Number", "Customer", "Product", "Amount", "Proposed EMI", "Status");
            System.out.println("-----------------------------------------------------------------------------------------");
            for (LoanApplication a : list) {
                System.out.printf("%-6d %-16s %-12s %-10s %-15s %-15s %-15s%n",
                        a.getApplicationId(),
                        a.getApplicationNumber(),
                        a.getCustomerId(),
                        a.getProduct(),
                        IndianCurrencyFormatter.format(a.getRequestedAmount()),
                        IndianCurrencyFormatter.format(a.getProposedEmi()),
                        a.getStatus());
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 3. PARAMETERIZED SQL REPORTS
    // -------------------------------------------------------------------------
    private void handleReportsMenu() {
        System.out.println("\n--- PARAMETERIZED ENTERPRISE SQL REPORTS ---");
        System.out.println("  1. Report 1: Application Register (Filterable)");
        System.out.println("  2. Report 2: Product & Branch Pipeline Summary");
        System.out.println("  3. Report 3: Disbursement Summary (Daily/Monthly/Quarterly/Branch)");
        System.out.println("  4. Report 4: Overdue Reviews (> 2 Calendar Days)");
        System.out.println("  5. Report 5: Customers Without Disbursement (0 Payouts)");
        System.out.println("  6. Report 6: Repeat Applicants (HAVING Count > 1)");
        System.out.println("  7. Return to Main Menu");

        String choice = prompt("Choose (1-7): ").trim();
        switch (choice) {
            case "1" -> runReport1();
            case "2" -> runReport2();
            case "3" -> runReport3();
            case "4" -> runReport4();
            case "5" -> runReport5();
            case "6" -> runReport6();
            case "7" -> { /* return */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    private void runReport1() {
        System.out.println("\n[Report 1: Application Register]");
        String pStr = prompt("Filter by Product (PERSONAL, EDUCATION, or Enter for all): ").trim();
        String sStr = prompt("Filter by Status (DRAFT, UNDER_REVIEW, etc., or Enter for all): ").trim();

        ProductType prod = !pStr.isEmpty() ? ProductType.fromString(pStr) : null;
        ApplicationStatus status = !sStr.isEmpty() ? ApplicationStatus.fromString(sStr) : null;

        List<ApplicationRegisterItem> list = reportService.getApplicationRegister(prod, status);
        System.out.printf("%-18s %-20s %-10s %-16s %-14s %-15s%n", "App #", "Customer", "Product", "Amount (INR)", "Proposed EMI", "Status");
        System.out.println("----------------------------------------------------------------------------------------------");
        for (ApplicationRegisterItem item : list) {
            System.out.printf("%-18s %-20s %-10s %-16s %-14s %-15s%n",
                    item.getApplicationNumber(), item.getCustomerName(), item.getProduct(),
                    IndianCurrencyFormatter.format(item.getRequestedAmount()),
                    IndianCurrencyFormatter.format(item.getProposedEmi()),
                    item.getStatus());
        }
    }

    private void runReport2() {
        System.out.println("\n[Report 2A: Product & Status Pipeline Summary]");
        List<PipelineSummaryItem> pList = reportService.getProductPipelineSummary();
        System.out.printf("%-15s %-20s %-12s %-20s%n", "Product", "Status", "Total Apps", "Total Volume (INR)");
        System.out.println("-------------------------------------------------------------------------");
        for (PipelineSummaryItem item : pList) {
            System.out.printf("%-15s %-20s %-12d %-20s%n",
                    item.getGroupKey1(), item.getGroupKey2(), item.getTotalApplications(),
                    IndianCurrencyFormatter.formatWithPrefix(item.getTotalRequestedAmount()));
        }

        System.out.println("\n[Report 2B: Branch Pipeline Summary]");
        List<PipelineSummaryItem> bList = reportService.getBranchPipelineSummary();
        System.out.printf("%-12s %-25s %-12s %-12s %-20s%n", "Branch Code", "Branch Name", "Product", "Total Apps", "Total Volume (INR)");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (PipelineSummaryItem item : bList) {
            System.out.printf("%-12s %-25s %-12s %-12d %-20s%n",
                    item.getGroupKey1(), item.getGroupKey2(), item.getGroupKey3(),
                    item.getTotalApplications(),
                    IndianCurrencyFormatter.formatWithPrefix(item.getTotalRequestedAmount()));
        }
    }

    private void runReport3() {
        System.out.println("\n[Report 3: Disbursement Summary]");
        OffsetDateTime start = OffsetDateTime.now().minusYears(1);
        OffsetDateTime end = OffsetDateTime.now().plusDays(1);

        System.out.println("Daily View:");
        List<DisbursementSummaryItem> dList = reportService.getDisbursementSummaryDaily(start, end);
        for (DisbursementSummaryItem item : dList) {
            System.out.printf("  Date: %-12s | Count: %-5d | Total Volume: %s%n",
                    item.getPeriodOrBranch(), item.getTotalDisbursements(),
                    IndianCurrencyFormatter.formatWithPrefix(item.getTotalDisbursedAmount()));
        }

        System.out.println("\nBranch-wise Breakdown:");
        List<DisbursementSummaryItem> bList = reportService.getDisbursementSummaryBranch(start, end);
        for (DisbursementSummaryItem item : bList) {
            System.out.printf("  Branch: %-25s | Product: %-10s | Count: %-5d | Total: %s%n",
                    item.getPeriodOrBranch(), item.getSecondaryInfo(), item.getTotalDisbursements(),
                    IndianCurrencyFormatter.formatWithPrefix(item.getTotalDisbursedAmount()));
        }
    }

    private void runReport4() {
        System.out.println("\n[Report 4: Overdue Reviews (> 2 Calendar Days)]");
        List<OverdueReviewItem> list = reportService.getOverdueReviews();
        if (list.isEmpty()) {
            System.out.println("No overdue applications currently waiting in UNDER_REVIEW status.");
            return;
        }
        System.out.printf("%-18s %-20s %-12s %-15s %-15s%n", "App #", "Customer", "Mobile", "Amount", "Days Waiting");
        System.out.println("--------------------------------------------------------------------------------");
        for (OverdueReviewItem item : list) {
            System.out.printf("%-18s %-20s %-12s %-15s %d days overdue%n",
                    item.getApplicationNumber(), item.getCustomerName(), item.getCustomerMobile(),
                    IndianCurrencyFormatter.format(item.getRequestedAmount()), item.getDaysWaiting());
        }
    }

    private void runReport5() {
        System.out.println("\n[Report 5: Customers Without Disbursement (0 Payouts)]");
        List<CustomerWithoutDisbursementItem> list = reportService.getCustomersWithoutDisbursement();
        System.out.printf("%-15s %-22s %-12s %-15s%n", "Customer ID", "Name", "Mobile", "Total Applications");
        System.out.println("-------------------------------------------------------------------------");
        for (CustomerWithoutDisbursementItem item : list) {
            System.out.printf("%-15s %-22s %-12s %-15d%n",
                    item.getCustomerId(), item.getCustomerName(), item.getMobile(), item.getTotalApplications());
        }
    }

    private void runReport6() {
        System.out.println("\n[Report 6: Repeat Applicants (HAVING COUNT > 1)]");
        List<RepeatApplicantItem> list = reportService.getRepeatApplicants();
        if (list.isEmpty()) {
            System.out.println("No repeat applicants found with > 1 loan application.");
            return;
        }
        System.out.printf("%-15s %-22s %-12s %-12s %-20s%n", "Customer ID", "Name", "Mobile", "App Count", "Total Requested Volume");
        System.out.println("-----------------------------------------------------------------------------------------");
        for (RepeatApplicantItem item : list) {
            System.out.printf("%-15s %-22s %-12s %-12d %-20s%n",
                    item.getCustomerId(), item.getCustomerName(), item.getMobile(),
                    item.getApplicationCount(),
                    IndianCurrencyFormatter.formatWithPrefix(item.getTotalRequestedAmount()));
        }
    }

    // -------------------------------------------------------------------------
    // 4. BACKGROUND UTILITIES & CSV
    // -------------------------------------------------------------------------
    private void handleUtilitiesMenu() {
        if (!currentUser.hasPermission(com.los.model.Permission.UTILITIES_EXECUTE)) {
            System.err.println("[SECURITY] Access Denied: Role [" + currentUser.getRole() + "] cannot execute background jobs.");
            return;
        }
        System.out.println("\n--- BACKGROUND JOBS & CSV UTILITIES ---");
        System.out.println("  1. Trigger Overdue Review Monitor Job");
        System.out.println("  2. Trigger Core Banking Push Utility Job");
        System.out.println("  3. Export Pending Reviews CSV");
        System.out.println("  4. Toggle Background Executor Scheduler (Current: " + (pipelineScheduler.isRunning() ? "ACTIVE" : "STOPPED") + ")");
        System.out.println("  5. Return to Main Menu");

        String choice = prompt("Choose (1-5): ").trim();
        switch (choice) {
            case "1" -> {
                System.out.println("Executing Overdue Review Monitor...");
                UtilityRun run = overdueMonitor.executeJob();
                System.out.println("Result: " + run.getResult() + " | Apps Processed: " + run.getApplicationsProcessed() + " | Alerts Created: " + run.getAlertsCreated());
            }
            case "2" -> {
                System.out.println("Executing Core Banking Push Utility...");
                UtilityRun run = coreBankingPushUtility.executeJob();
                System.out.println("Result: " + run.getResult() + " | Apps Processed: " + run.getApplicationsProcessed());
            }
            case "3" -> {
                String csv = overdueMonitor.generateCsvString();
                String filename = "pending_reviews_" + config.getBusinessDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
                System.out.println("Generated CSV Preview (" + filename + "):");
                System.out.println("--------------------------------------------------");
                System.out.println(csv);
            }
            case "4" -> {
                if (pipelineScheduler.isRunning()) {
                    pipelineScheduler.stop();
                    System.out.println("Background Scheduler STOPPED.");
                } else {
                    pipelineScheduler.start();
                    System.out.println("Background Scheduler STARTED.");
                }
            }
            case "5" -> { /* return */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    // -------------------------------------------------------------------------
    // 5. SYSTEM SETTINGS & WEB SERVER
    // -------------------------------------------------------------------------
    private void handleSystemMenu() {
        System.out.println("\n--- SYSTEM & WEB SERVER SETTINGS ---");
        System.out.println("  1. Start Embedded Web Server (http://localhost:8080)");
        System.out.println("  2. Test Database Connection (Supabase Session Pooler)");
        System.out.println("  3. View Active Configuration & Product Policies");
        System.out.println("  4. Return to Main Menu");

        String choice = prompt("Choose (1-4): ").trim();
        switch (choice) {
            case "1" -> {
                if (webServer == null) {
                    try {
                        webServer = new LosWebServer();
                        webServer.start();
                        System.out.println("Open your browser to http://localhost:" + config.getServerPort());
                    } catch (Exception e) {
                        System.err.println("Failed to start web server: " + e.getMessage());
                    }
                } else {
                    System.out.println("Web server is already running on port " + config.getServerPort());
                }
            }
            case "2" -> {
                boolean ok = DatabaseConfig.testConnection();
                System.out.println("Database Status: " + (ok ? "CONNECTED (PostgreSQL session pooler responsive)" : "FAILED to connect"));
            }
            case "3" -> {
                System.out.println("\nActive Policy Settings:");
                System.out.println("  Business Date:       " + config.getBusinessDate());
                System.out.println("  Age Boundaries:      " + config.getMinAge() + " to " + config.getMaxAge() + " years");
                System.out.println("  Max Allowed FOIR:    " + config.getMaxFoirPercentage() + "%");
                System.out.println("  Overdue Threshold:   " + config.getOverdueThresholdDays() + " calendar days");
                System.out.println("  PERSONAL Loan:       Amount " + config.getPersonalMinAmount() + " - " + config.getPersonalMaxAmount() +
                        ", Tenure " + config.getPersonalMinTenure() + " - " + config.getPersonalMaxTenure() + "m, Rate " + config.getPersonalInterestRate() + "%");
                System.out.println("  EDUCATION Loan:      Amount " + config.getEducationMinAmount() + " - " + config.getEducationMaxAmount() +
                        ", Tenure " + config.getEducationMinTenure() + " - " + config.getEducationMaxTenure() + "m, Rate " + config.getEducationInterestRate() + "%");
            }
            case "4" -> { /* return */ }
            default -> System.out.println("Invalid choice.");
        }
    }

    private void handleSwitchRoleMenu() {
        System.out.println("\n--- DYNAMIC ROLE SWITCHER (RBAC) ---");
        System.out.println("  1. LOAN_OFFICER (Ramesh Sharma - Sourcing & Submissions)");
        System.out.println("  2. CREDIT_OFFICER (Priya Iyer - Underwriting & Approval/Rejection)");
        System.out.println("  3. DISBURSEMENT_OFFICER (Amit Patel - Loan Payouts)");
        System.out.println("  4. AUDITOR (Neha Kulkarni - Read-Only Oversight)");
        System.out.println("  5. ADMIN (Rajesh Gupta - Full Unrestricted Access)");

        String choice = prompt("Select Role (1-5): ").trim();
        switch (choice) {
            case "1" -> currentUser = new UserContext("officer_ramesh", com.los.model.UserRole.LOAN_OFFICER);
            case "2" -> currentUser = new UserContext("credit_priya", com.los.model.UserRole.CREDIT_OFFICER);
            case "3" -> currentUser = new UserContext("treasury_amit", com.los.model.UserRole.DISBURSEMENT_OFFICER);
            case "4" -> currentUser = new UserContext("auditor_neha", com.los.model.UserRole.AUDITOR);
            case "5" -> currentUser = new UserContext("admin_super", com.los.model.UserRole.ADMIN);
            default -> System.out.println("Invalid selection. Role not changed.");
        }
        System.out.println(">>> Active Security Context switched to: " + currentUser.getUsername() + " (" + currentUser.getRole() + ")");
    }

    private String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine();
    }
}
