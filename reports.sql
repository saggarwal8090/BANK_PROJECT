-- ==============================================================================
-- Mini Loan Origination System (LOS) - Parameterized SQL Reports
-- PostgreSQL / Supabase Compatible
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- Report 1: Application Register
-- Filterable by optional Product and Status (pass NULL or exact string)
-- ------------------------------------------------------------------------------
-- Parameters:
--   $1: product (e.g., 'PERSONAL', 'EDUCATION', or NULL for all)
--   $2: status (e.g., 'DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL', etc., or NULL for all)
SELECT 
    la.application_number,
    c.name AS customer_name,
    c.mobile AS customer_mobile,
    la.product,
    la.requested_amount,
    la.proposed_emi,
    la.status,
    la.submitted_at,
    la.branch_code,
    la.branch_name,
    la.eligible
FROM loan_application la
JOIN customer c ON la.customer_id = c.customer_id
WHERE (? IS NULL OR la.product = ?)
  AND (? IS NULL OR la.status = ?)
ORDER BY la.created_at DESC;


-- ------------------------------------------------------------------------------
-- Report 2A: Product & Status Pipeline Summary
-- Count and sum of requested amount grouped by (product, status)
-- ------------------------------------------------------------------------------
SELECT 
    product,
    status,
    COUNT(*) AS total_applications,
    COALESCE(SUM(requested_amount), 0.00) AS total_requested_amount
FROM loan_application
GROUP BY product, status
ORDER BY product ASC, status ASC;


-- ------------------------------------------------------------------------------
-- Report 2B: Branch & Product Pipeline Summary
-- Count and sum of requested amount grouped by (branch_code, branch_name, product)
-- ------------------------------------------------------------------------------
SELECT 
    branch_code,
    branch_name,
    product,
    COUNT(*) AS total_applications,
    COALESCE(SUM(requested_amount), 0.00) AS total_requested_amount
FROM loan_application
GROUP BY branch_code, branch_name, product
ORDER BY branch_code ASC, product ASC;


-- ------------------------------------------------------------------------------
-- Report 3A: Disbursement Summary (Daily View with Date Range)
-- Parameters:
--   $1: start_date (e.g., '2026-01-01 00:00:00+00')
--   $2: end_date   (e.g., '2026-12-31 23:59:59+00')
-- ------------------------------------------------------------------------------
SELECT 
    DATE(d.disbursed_at) AS disbursement_date,
    COUNT(d.disbursement_id) AS total_disbursements,
    COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
FROM disbursement d
WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
GROUP BY DATE(d.disbursed_at)
ORDER BY disbursement_date DESC;


-- ------------------------------------------------------------------------------
-- Report 3B: Disbursement Summary (Branch-wise Breakdown)
-- Parameters:
--   $1: start_date
--   $2: end_date
-- ------------------------------------------------------------------------------
SELECT 
    la.branch_code,
    la.branch_name,
    la.product,
    COUNT(d.disbursement_id) AS total_disbursements,
    COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
FROM disbursement d
JOIN loan_application la ON d.application_id = la.application_id
WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
GROUP BY la.branch_code, la.branch_name, la.product
ORDER BY la.branch_code ASC, la.product ASC;


-- ------------------------------------------------------------------------------
-- Report 3C: Disbursement Summary (Monthly View)
-- Parameters:
--   $1: start_date
--   $2: end_date
-- ------------------------------------------------------------------------------
SELECT 
    TO_CHAR(d.disbursed_at, 'YYYY-MM') AS disbursement_month,
    COUNT(d.disbursement_id) AS total_disbursements,
    COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
FROM disbursement d
WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
GROUP BY TO_CHAR(d.disbursed_at, 'YYYY-MM')
ORDER BY disbursement_month DESC;


-- ------------------------------------------------------------------------------
-- Report 3D: Disbursement Summary (Quarterly View)
-- Parameters:
--   $1: start_date
--   $2: end_date
-- ------------------------------------------------------------------------------
SELECT 
    TO_CHAR(d.disbursed_at, 'YYYY-"Q"Q') AS disbursement_quarter,
    COUNT(d.disbursement_id) AS total_disbursements,
    COALESCE(SUM(d.amount), 0.00) AS total_disbursed_amount
FROM disbursement d
WHERE d.disbursed_at >= ? AND d.disbursed_at <= ?
GROUP BY TO_CHAR(d.disbursed_at, 'YYYY-"Q"Q')
ORDER BY disbursement_quarter DESC;


-- ------------------------------------------------------------------------------
-- Report 4: Overdue Reviews
-- Applications in UNDER_REVIEW status submitted > 2 calendar days ago relative to Business Date
-- Parameters:
--   $1: business_date (e.g., '2026-11-24')
-- ------------------------------------------------------------------------------
SELECT 
    la.application_id,
    la.application_number,
    c.customer_id,
    c.name AS customer_name,
    c.mobile AS customer_mobile,
    la.product,
    la.requested_amount,
    la.submitted_at,
    (?::DATE - la.submitted_at::DATE) AS days_waiting
FROM loan_application la
JOIN customer c ON la.customer_id = c.customer_id
WHERE la.status = 'UNDER_REVIEW'
  AND la.submitted_at IS NOT NULL
  AND (?::DATE - la.submitted_at::DATE) > 2
ORDER BY days_waiting DESC, la.submitted_at ASC;


-- ------------------------------------------------------------------------------
-- Report 5: Customers Without Disbursement
-- Distinct customers having >= 1 application and 0 disbursements across all applications
-- ------------------------------------------------------------------------------
SELECT 
    c.customer_id,
    c.name AS customer_name,
    c.mobile,
    c.created_at AS registration_date,
    COUNT(la.application_id) AS total_applications
FROM customer c
JOIN loan_application la ON c.customer_id = la.customer_id
LEFT JOIN disbursement d ON la.application_id = d.application_id
GROUP BY c.customer_id, c.name, c.mobile, c.created_at
HAVING COUNT(d.disbursement_id) = 0
ORDER BY c.name ASC;


-- ------------------------------------------------------------------------------
-- Report 6: Repeat Applicants
-- Customers with > 1 application showing application count and total requested amount
-- ------------------------------------------------------------------------------
SELECT 
    c.customer_id,
    c.name AS customer_name,
    c.mobile,
    COUNT(la.application_id) AS application_count,
    COALESCE(SUM(la.requested_amount), 0.00) AS total_requested_amount
FROM customer c
JOIN loan_application la ON c.customer_id = la.customer_id
GROUP BY c.customer_id, c.name, c.mobile
HAVING COUNT(la.application_id) > 1
ORDER BY application_count DESC, total_requested_amount DESC;
