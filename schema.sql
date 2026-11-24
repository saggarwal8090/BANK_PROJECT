-- ==============================================================================
-- Mini Loan Origination System (LOS) - PostgreSQL / Supabase DDL Schema
-- Compatible with PostgreSQL 14+ / Supabase Session Pooler (port 5432)
-- ==============================================================================

-- 1. Customer Table
CREATE TABLE IF NOT EXISTS customer (
    customer_id VARCHAR(15) PRIMARY KEY, -- Generated in Java: CUST-XXXXXX
    name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    mobile VARCHAR(10) UNIQUE NOT NULL CHECK (mobile ~ '^[0-9]{10}$'),
    address TEXT NOT NULL,
    kyc_details TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Loan Application Table
CREATE TABLE IF NOT EXISTS loan_application (
    application_id BIGSERIAL PRIMARY KEY,
    application_number VARCHAR(30) UNIQUE NOT NULL, -- Format: APP-YYYY-XXXXXX
    customer_id VARCHAR(15) NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
    product VARCHAR(20) NOT NULL CHECK (product IN ('PERSONAL', 'EDUCATION')),
    branch_code VARCHAR(20) NOT NULL,
    branch_name VARCHAR(100) NOT NULL,
    requested_amount NUMERIC(15, 2) NOT NULL CHECK (requested_amount > 0),
    tenure_months INT NOT NULL CHECK (tenure_months > 0),
    monthly_income NUMERIC(15, 2) NOT NULL CHECK (monthly_income > 0),
    existing_monthly_obligations NUMERIC(15, 2) NOT NULL CHECK (existing_monthly_obligations >= 0),
    annual_interest_rate NUMERIC(5, 2) NOT NULL CHECK (annual_interest_rate > 0),
    proposed_emi NUMERIC(15, 2) NOT NULL CHECK (proposed_emi >= 0),
    foir_percentage NUMERIC(5, 2) NOT NULL CHECK (foir_percentage >= 0),
    eligible BOOLEAN NOT NULL DEFAULT FALSE,
    eligibility_reasons TEXT,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL', 'REJECTED', 'DISBURSED', 'CORE_BANKING_PUSH', 'EXIT'
    )),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP WITH TIME ZONE,
    decided_at TIMESTAMP WITH TIME ZONE,
    reviewer_code VARCHAR(50),
    decision_reason TEXT
);

-- Partial Unique Index: Max 1 active application (DRAFT, UNDER_REVIEW, CREDIT_APPROVAL) per (customer_id, product)
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_app_customer_product 
ON loan_application (customer_id, product) 
WHERE status IN ('DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL');

CREATE INDEX IF NOT EXISTS idx_loan_app_customer_id ON loan_application (customer_id);
CREATE INDEX IF NOT EXISTS idx_loan_app_status ON loan_application (status);
CREATE INDEX IF NOT EXISTS idx_loan_app_submitted_at ON loan_application (submitted_at);

-- 3. Disbursement Table
CREATE TABLE IF NOT EXISTS disbursement (
    disbursement_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT UNIQUE NOT NULL REFERENCES loan_application(application_id) ON DELETE RESTRICT,
    reference VARCHAR(50) UNIQUE NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    disbursed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_disbursement_date ON disbursement (disbursed_at);

-- 4. Application History / Audit Trail Table
CREATE TABLE IF NOT EXISTS application_history (
    history_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES loan_application(application_id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    performed_by VARCHAR(50) NOT NULL,
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks TEXT
);

CREATE INDEX IF NOT EXISTS idx_app_history_app_id ON application_history (application_id);

-- 5. System Alerts Table
CREATE TABLE IF NOT EXISTS system_alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES loan_application(application_id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_system_alerts_app_type UNIQUE (application_id, alert_type)
);

CREATE INDEX IF NOT EXISTS idx_system_alerts_type ON system_alerts (alert_type);

-- 6. Utility Run Log Table
CREATE TABLE IF NOT EXISTS utility_run (
    run_id BIGSERIAL PRIMARY KEY,
    utility_name VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    result VARCHAR(30) NOT NULL,
    applications_processed INT NOT NULL DEFAULT 0,
    alerts_created INT NOT NULL DEFAULT 0,
    error_message TEXT
);

-- 7. Next-Gen Core Banking Integration Push Table
CREATE TABLE IF NOT EXISTS ng_core_banking (
    core_id BIGSERIAL PRIMARY KEY,
    application_id BIGINT UNIQUE NOT NULL REFERENCES loan_application(application_id) ON DELETE RESTRICT,
    customer_id VARCHAR(15) NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
    customer_name VARCHAR(100) NOT NULL,
    address TEXT NOT NULL,
    dob DATE NOT NULL,
    kyc_details TEXT NOT NULL,
    approved_amount NUMERIC(15, 2) NOT NULL,
    disbursed_amount NUMERIC(15, 2) NOT NULL,
    disbursed_date DATE NOT NULL,
    synced_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ng_core_customer_id ON ng_core_banking (customer_id);

-- 8. Enterprise Role-Based Access Control (RBAC) User Table
CREATE TABLE IF NOT EXISTS app_user (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN (
        'LOAN_OFFICER', 'CREDIT_OFFICER', 'DISBURSEMENT_OFFICER', 'ADMIN', 'AUDITOR'
    )),
    email VARCHAR(100) UNIQUE NOT NULL,
    branch_code VARCHAR(20) DEFAULT 'BR001',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_user_role ON app_user (role);

-- Seed Enterprise RBAC Users
INSERT INTO app_user (username, full_name, role, email, branch_code) VALUES
('officer_ramesh', 'Ramesh Sharma', 'LOAN_OFFICER', 'ramesh@losbank.in', 'BR001'),
('credit_priya', 'Priya Iyer', 'CREDIT_OFFICER', 'priya@losbank.in', 'BR001'),
('treasury_amit', 'Amit Patel', 'DISBURSEMENT_OFFICER', 'amit@losbank.in', 'BR001'),
('admin_super', 'Rajesh Gupta (Admin)', 'ADMIN', 'admin@losbank.in', 'HQ001'),
('auditor_neha', 'Neha Kulkarni', 'AUDITOR', 'neha@losbank.in', 'AUDIT01')
ON CONFLICT (username) DO NOTHING;

