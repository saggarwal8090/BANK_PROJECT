package com.los.web;

import com.los.config.AppConfig;
import com.los.config.DatabaseConfig;
import com.los.config.SupabaseClient;
import com.los.dao.AppUserDAO;
import com.los.model.AccessDeniedException;
import com.los.model.AppUser;
import com.los.model.ApplicationStatus;
import com.los.model.Customer;
import com.los.model.DuplicateEntityException;
import com.los.model.EntityNotFoundException;
import com.los.model.InvalidStateTransitionException;
import com.los.model.LoanApplication;
import com.los.model.Permission;
import com.los.model.ProductType;
import com.los.model.UserContext;
import com.los.model.UserRole;
import com.los.model.UtilityRun;
import com.los.model.ValidationException;
import com.los.service.CustomerService;
import com.los.service.LoanApplicationService;
import com.los.service.ReportService;
import com.los.utility.CoreBankingPushUtility;
import com.los.utility.OverdueReviewMonitor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise HTTP Request Router & Dynamic Role-Based Access Control (RBAC) Controller.
 */
public class LosHttpHandler implements HttpHandler {

    private final CustomerService customerService;
    private final LoanApplicationService applicationService;
    private final ReportService reportService;
    private final OverdueReviewMonitor overdueMonitor;
    private final CoreBankingPushUtility coreBankingPushUtility;
    private final AppUserDAO userDAO;
    private final SupabaseClient supabaseClient;
    private final AppConfig config;

    public LosHttpHandler() {
        this.customerService = new CustomerService();
        this.applicationService = new LoanApplicationService();
        this.reportService = new ReportService();
        this.overdueMonitor = new OverdueReviewMonitor();
        this.coreBankingPushUtility = new CoreBankingPushUtility();
        this.userDAO = new AppUserDAO();
        this.supabaseClient = new SupabaseClient();
        this.config = AppConfig.getInstance();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();

        // 1. Handle CORS Preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        addCorsHeaders(exchange);

        try {
            // Resolve Dynamic User Security Context from Headers
            UserContext userContext = resolveUserContext(exchange);

            // Static Files
            if (path.equals("/") || path.equals("/index.html") || path.startsWith("/public/")) {
                serveStaticFile(exchange, path);
                return;
            }

            // Health & System Info (Public)
            if (path.equals("/api/health") && "GET".equalsIgnoreCase(method)) {
                handleHealth(exchange);
                return;
            }

            // User & Role Management Endpoint
            if (path.equals("/api/users") && "GET".equalsIgnoreCase(method)) {
                handleListUsers(exchange);
                return;
            }

            // Customer REST Endpoints
            if (path.equals("/api/customers")) {
                if ("GET".equalsIgnoreCase(method)) {
                    userContext.checkPermission(Permission.CUSTOMER_READ);
                    handleListCustomers(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    userContext.checkPermission(Permission.CUSTOMER_CREATE);
                    handleCreateCustomer(exchange);
                } else {
                    sendMethodNotAllowed(exchange);
                }
                return;
            }

            if (path.startsWith("/api/customers/")) {
                String id = path.substring("/api/customers/".length());
                if ("GET".equalsIgnoreCase(method)) {
                    userContext.checkPermission(Permission.CUSTOMER_READ);
                    handleGetCustomer(exchange, id);
                } else {
                    sendMethodNotAllowed(exchange);
                }
                return;
            }

            // Application REST Endpoints
            if (path.equals("/api/applications")) {
                if ("GET".equalsIgnoreCase(method)) {
                    userContext.checkPermission(Permission.APPLICATION_READ);
                    handleListApplications(exchange);
                } else if ("POST".equalsIgnoreCase(method)) {
                    userContext.checkPermission(Permission.APPLICATION_DRAFT_CREATE);
                    handleCreateDraftApplication(exchange, userContext);
                } else {
                    sendMethodNotAllowed(exchange);
                }
                return;
            }

            if (path.startsWith("/api/applications/")) {
                handleApplicationSubpaths(exchange, path, method, userContext);
                return;
            }

            // Report Endpoints
            if (path.startsWith("/api/reports/")) {
                userContext.checkPermission(Permission.REPORTS_VIEW);
                handleReports(exchange, path);
                return;
            }

            // Utility Runner & CSV Download Endpoints
            if (path.equals("/api/utilities/run") && ("POST".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method))) {
                userContext.checkPermission(Permission.UTILITIES_EXECUTE);
                handleRunUtilities(exchange);
                return;
            }

            if (path.equals("/api/utilities/csv") && "GET".equalsIgnoreCase(method)) {
                userContext.checkPermission(Permission.REPORTS_VIEW);
                handleDownloadCsv(exchange);
                return;
            }

            // Fallback 404
            sendJsonResponse(exchange, 404, "{\"error\":\"Resource not found: " + JsonUtils.escape(path) + "\"}");

        } catch (AccessDeniedException e) {
            sendJsonResponse(exchange, 403, "{\"error\":\"" + JsonUtils.escape(e.getMessage()) + "\"}");
        } catch (ValidationException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"" + JsonUtils.escape(e.getMessage()) + "\"}");
        } catch (DuplicateEntityException e) {
            sendJsonResponse(exchange, 409, "{\"error\":\"" + JsonUtils.escape(e.getMessage()) + "\"}");
        } catch (EntityNotFoundException e) {
            sendJsonResponse(exchange, 404, "{\"error\":\"" + JsonUtils.escape(e.getMessage()) + "\"}");
        } catch (InvalidStateTransitionException e) {
            sendJsonResponse(exchange, 422, "{\"error\":\"" + JsonUtils.escape(e.getMessage()) + "\"}");
        } catch (Exception e) {
            System.err.println("[LosHttpHandler] Unhandled Error: " + e.getMessage());
            sendJsonResponse(exchange, 500, "{\"error\":\"Internal Server Error: " + JsonUtils.escape(e.getMessage()) + "\"}");
        }
    }

    /**
     * Resolves caller UserContext dynamically from HTTP headers.
     * Checks Authorization (Bearer Supabase key), X-User-Role, and X-User-Name.
     */
    private UserContext resolveUserContext(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String roleHeader = exchange.getRequestHeaders().getFirst("X-User-Role");
        String userHeader = exchange.getRequestHeaders().getFirst("X-User-Name");

        // 1. Supabase Secret Key -> Elevated ADMIN role
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (token.equals(config.getSupabaseSecretKey())) {
                return new UserContext("supabase_admin", UserRole.ADMIN, token);
            }
        }

        // 2. Dynamic Role Header
        if (roleHeader != null && !roleHeader.trim().isEmpty()) {
            UserRole role = UserRole.fromString(roleHeader);
            String name = (userHeader != null && !userHeader.trim().isEmpty()) ? userHeader.trim() : role.name().toLowerCase();
            return new UserContext(name, role);
        }

        // Default User Context
        return UserContext.DEFAULT_OFFICER;
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        String dbProduct = "Unknown";
        String dbVersion = "Unknown";
        String dbHost = com.los.config.DBConnection.getDatabaseHost();
        String maskedUrl = com.los.config.DBConnection.getMaskedUrl();
        boolean dbOk = false;
        String dbError = null;

        try (Connection conn = com.los.config.DBConnection.getConnection()) {
            dbOk = conn != null && conn.isValid(5);
            if (conn != null) {
                dbProduct = com.los.config.DBConnection.getDatabaseProductName(conn);
                dbVersion = com.los.config.DBConnection.getDatabaseProductVersion(conn);
            }
        } catch (SQLException e) {
            dbOk = false;
            dbError = e.getMessage();
        }

        int statusCode = dbOk ? 200 : 503;
        String json = String.format(
            "{\"status\":\"%s\",\"database\":{\"connected\":%b,\"product\":\"%s\",\"version\":\"%s\",\"host\":\"%s\",\"url\":\"%s\",\"user\":\"%s\",\"error\":%s},\"businessDate\":\"%s\",\"serverPort\":%d,\"supabase\":{\"url\":\"%s\",\"projectRef\":\"ciemfotaaupazeqxxuev\"}}",
            dbOk ? "UP" : "DOWN",
            dbOk,
            JsonUtils.escape(dbProduct),
            JsonUtils.escape(dbVersion),
            JsonUtils.escape(dbHost),
            JsonUtils.escape(maskedUrl),
            JsonUtils.escape(com.los.config.DBConnection.getDbUser()),
            dbError != null ? "\"" + JsonUtils.escape(dbError) + "\"" : "null",
            config.getBusinessDate().toString(),
            config.getServerPort(),
            config.getSupabaseUrl()
        );
        sendJsonResponse(exchange, statusCode, json);
    }

    private void handleListUsers(HttpExchange exchange) throws IOException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            List<AppUser> users = userDAO.findAll(conn);
            sendJsonResponse(exchange, 200, JsonUtils.userListToJson(users));
        } catch (SQLException e) {
            sendJsonResponse(exchange, 500, "{\"error\":\"Database error fetching users: " + JsonUtils.escape(e.getMessage()) + "\"}");
        }
    }

    private void handleListCustomers(HttpExchange exchange) throws IOException {
        var list = customerService.getAllCustomers();
        sendJsonResponse(exchange, 200, JsonUtils.customerListToJson(list));
    }

    private void handleCreateCustomer(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        Map<String, String> map = JsonUtils.parseJsonObject(body);

        Customer c = new Customer();
        c.setName(map.get("name"));
        c.setMobile(map.get("mobile"));
        String dobStr = map.get("dateOfBirth");
        if (dobStr != null && !dobStr.trim().isEmpty()) {
            c.setDateOfBirth(LocalDate.parse(dobStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE));
        }
        c.setAddress(map.get("address"));
        c.setKycDetails(map.get("kycDetails"));

        Customer created = customerService.registerCustomer(c);
        sendJsonResponse(exchange, 201, JsonUtils.toJson(created));
    }

    private void handleGetCustomer(HttpExchange exchange, String id) throws IOException {
        Customer c = customerService.getCustomerById(id);
        sendJsonResponse(exchange, 200, JsonUtils.toJson(c));
    }

    private void handleListApplications(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
        ProductType prod = null;
        ApplicationStatus status = null;

        if (query.containsKey("product") && !query.get("product").isEmpty()) {
            prod = ProductType.fromString(query.get("product"));
        }
        if (query.containsKey("status") && !query.get("status").isEmpty()) {
            status = ApplicationStatus.fromString(query.get("status"));
        }

        var list = applicationService.filterApplications(prod, status);
        sendJsonResponse(exchange, 200, JsonUtils.applicationListToJson(list));
    }

    private void handleCreateDraftApplication(HttpExchange exchange, UserContext userContext) throws IOException {
        String body = readRequestBody(exchange);
        Map<String, String> map = JsonUtils.parseJsonObject(body);

        LoanApplication app = new LoanApplication();
        app.setCustomerId(map.get("customerId"));
        app.setProduct(ProductType.fromString(map.get("product")));
        app.setBranchCode(map.get("branchCode"));
        app.setBranchName(map.get("branchName"));
        if (map.containsKey("requestedAmount")) {
            app.setRequestedAmount(new BigDecimal(map.get("requestedAmount").trim()));
        }
        if (map.containsKey("tenureMonths")) {
            app.setTenureMonths(Integer.parseInt(map.get("tenureMonths").trim()));
        }
        if (map.containsKey("monthlyIncome")) {
            app.setMonthlyIncome(new BigDecimal(map.get("monthlyIncome").trim()));
        }
        if (map.containsKey("existingMonthlyObligations") && !map.get("existingMonthlyObligations").trim().isEmpty()) {
            app.setExistingMonthlyObligations(new BigDecimal(map.get("existingMonthlyObligations").trim()));
        } else {
            app.setExistingMonthlyObligations(BigDecimal.ZERO);
        }

        LoanApplication created = applicationService.createDraft(app, userContext.getUsername());
        sendJsonResponse(exchange, 201, JsonUtils.toJson(created));
    }

    private void handleApplicationSubpaths(HttpExchange exchange, String path, String method, UserContext userContext) throws IOException {
        String sub = path.substring("/api/applications/".length());
        String[] parts = sub.split("/");
        Long appId = Long.parseLong(parts[0]);

        if (parts.length == 1) {
            if ("GET".equalsIgnoreCase(method)) {
                userContext.checkPermission(Permission.APPLICATION_READ);
                LoanApplication app = applicationService.getApplicationById(appId);
                sendJsonResponse(exchange, 200, JsonUtils.toJson(app));
            } else if ("PUT".equalsIgnoreCase(method)) {
                userContext.checkPermission(Permission.APPLICATION_DRAFT_EDIT);
                String body = readRequestBody(exchange);
                Map<String, String> map = JsonUtils.parseJsonObject(body);

                LoanApplication app = new LoanApplication();
                app.setApplicationId(appId);
                app.setProduct(ProductType.fromString(map.get("product")));
                app.setBranchCode(map.get("branchCode"));
                app.setBranchName(map.get("branchName"));
                if (map.containsKey("requestedAmount")) {
                    app.setRequestedAmount(new BigDecimal(map.get("requestedAmount").trim()));
                }
                if (map.containsKey("tenureMonths")) {
                    app.setTenureMonths(Integer.parseInt(map.get("tenureMonths").trim()));
                }
                if (map.containsKey("monthlyIncome")) {
                    app.setMonthlyIncome(new BigDecimal(map.get("monthlyIncome").trim()));
                }
                if (map.containsKey("existingMonthlyObligations")) {
                    app.setExistingMonthlyObligations(new BigDecimal(map.get("existingMonthlyObligations").trim()));
                }

                LoanApplication updated = applicationService.updateDraft(app, userContext.getUsername());
                sendJsonResponse(exchange, 200, JsonUtils.toJson(updated));
            } else {
                sendMethodNotAllowed(exchange);
            }
            return;
        }

        String action = parts[1];
        if ("history".equalsIgnoreCase(action) && "GET".equalsIgnoreCase(method)) {
            userContext.checkPermission(Permission.APPLICATION_READ);
            var history = applicationService.getApplicationHistory(appId);
            sendJsonResponse(exchange, 200, JsonUtils.historyListToJson(history));
            return;
        }

        if ("submit".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
            userContext.checkPermission(Permission.APPLICATION_SUBMIT);
            LoanApplication submitted = applicationService.submitApplication(appId, userContext.getUsername());
            sendJsonResponse(exchange, 200, JsonUtils.toJson(submitted));
            return;
        }

        if ("review".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
            userContext.checkPermission(Permission.APPLICATION_REVIEW);
            String body = readRequestBody(exchange);
            Map<String, String> map = JsonUtils.parseJsonObject(body);
            boolean approve = Boolean.parseBoolean(map.getOrDefault("approve", "false"));
            String reviewer = map.getOrDefault("reviewerCode", userContext.getUsername());
            String reason = map.getOrDefault("decisionReason", "");

            LoanApplication reviewed = applicationService.reviewApplication(appId, approve, reviewer, reason, userContext.getUsername());
            sendJsonResponse(exchange, 200, JsonUtils.toJson(reviewed));
            return;
        }

        if ("disburse".equalsIgnoreCase(action) && "POST".equalsIgnoreCase(method)) {
            userContext.checkPermission(Permission.APPLICATION_DISBURSE);
            String body = readRequestBody(exchange);
            Map<String, String> map = JsonUtils.parseJsonObject(body);
            String ref = map.get("reference");

            var disb = applicationService.disburseLoan(appId, ref, userContext.getUsername());
            sendJsonResponse(exchange, 200, JsonUtils.toJson(disb));
            return;
        }

        sendMethodNotAllowed(exchange);
    }

    private void handleReports(HttpExchange exchange, String path) throws IOException {
        if (path.equals("/api/reports/register")) {
            Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
            ProductType pt = q.containsKey("product") && !q.get("product").isEmpty() ? ProductType.fromString(q.get("product")) : null;
            ApplicationStatus st = q.containsKey("status") && !q.get("status").isEmpty() ? ApplicationStatus.fromString(q.get("status")) : null;
            var list = reportService.getApplicationRegister(pt, st);
            sendJsonResponse(exchange, 200, JsonUtils.registerReportToJson(list));
            return;
        }

        if (path.equals("/api/reports/pipeline/product")) {
            var list = reportService.getProductPipelineSummary();
            sendJsonResponse(exchange, 200, JsonUtils.pipelineReportToJson(list));
            return;
        }

        if (path.equals("/api/reports/pipeline/branch")) {
            var list = reportService.getBranchPipelineSummary();
            sendJsonResponse(exchange, 200, JsonUtils.pipelineReportToJson(list));
            return;
        }

        if (path.equals("/api/reports/disbursements")) {
            OffsetDateTime start = OffsetDateTime.now().minusDays(365);
            OffsetDateTime end = OffsetDateTime.now().plusDays(1);
            var list = reportService.getDisbursementSummaryDaily(start, end);
            sendJsonResponse(exchange, 200, JsonUtils.disbursementReportToJson(list));
            return;
        }

        if (path.equals("/api/reports/overdue")) {
            var list = reportService.getOverdueReviews();
            sendJsonResponse(exchange, 200, JsonUtils.overdueReportToJson(list));
            return;
        }

        if (path.equals("/api/reports/no-disbursement")) {
            var list = reportService.getCustomersWithoutDisbursement();
            sendJsonResponse(exchange, 200, JsonUtils.noDisbursementReportToJson(list));
            return;
        }

        if (path.equals("/api/reports/repeat-applicants")) {
            var list = reportService.getRepeatApplicants();
            sendJsonResponse(exchange, 200, JsonUtils.repeatApplicantsReportToJson(list));
            return;
        }

        sendJsonResponse(exchange, 404, "{\"error\":\"Report not found: " + JsonUtils.escape(path) + "\"}");
    }

    private void handleRunUtilities(HttpExchange exchange) throws IOException {
        UtilityRun run1 = overdueMonitor.executeJob();
        UtilityRun run2 = coreBankingPushUtility.executeJob();

        String json = String.format(
            "{\"status\":\"SUCCESS\",\"overdueMonitor\":{\"processed\":%d,\"alerts\":%d,\"result\":\"%s\"},\"coreBankingPush\":{\"processed\":%d,\"result\":\"%s\"}}",
            run1.getApplicationsProcessed(), run1.getAlertsCreated(), run1.getResult().name(),
            run2.getApplicationsProcessed(), run2.getResult().name()
        );
        sendJsonResponse(exchange, 200, json);
    }

    private void handleDownloadCsv(HttpExchange exchange) throws IOException {
        String csv = overdueMonitor.generateCsvString();
        String dateStr = config.getBusinessDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = "pending_reviews_" + dateStr + ".csv";

        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void serveStaticFile(HttpExchange exchange, String path) throws IOException {
        String relPath = path.equals("/") || path.equals("/index.html") ? "public/index.html" : path.substring(1);
        File file = new File(relPath);

        byte[] content = null;
        if (file.exists() && file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                content = fis.readAllBytes();
            }
        } else {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(relPath)) {
                if (is != null) {
                    content = is.readAllBytes();
                }
            }
        }

        if (content != null) {
            String contentType = relPath.endsWith(".html") ? "text/html; charset=UTF-8"
                    : relPath.endsWith(".css") ? "text/css"
                    : relPath.endsWith(".js") ? "application/javascript"
                    : "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } else {
            sendJsonResponse(exchange, 404, "{\"error\":\"File not found: " + JsonUtils.escape(relPath) + "\"}");
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, X-User-Role, X-User-Name");
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }
}
