// ==============================================================================
// Production-grade Vercel Serverless Function — Mini LOS (Newgen Assignment)
// Strictly connected to Supabase PostgreSQL. NO in-memory fallbacks.
// All business rules match Assignment_1_Lending.pdf exactly.
// ==============================================================================

import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import pg from 'pg';

const { Pool } = pg;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// ---------------------------------------------------------------------------
// Config: load config.properties for local runs
// ---------------------------------------------------------------------------
function loadConfigProperties() {
  const props = {};
  const searchPaths = [
    path.join(process.cwd(), 'config.properties'),
    path.join(__dirname, '..', 'config.properties'),
    path.join(__dirname, '..', 'src', 'main', 'resources', 'config.properties')
  ];
  for (const p of searchPaths) {
    if (fs.existsSync(p)) {
      try {
        const content = fs.readFileSync(p, 'utf8');
        content.split(/\r?\n/).forEach(line => {
          const trimmed = line.trim();
          if (trimmed && !trimmed.startsWith('#') && trimmed.includes('=')) {
            const idx = trimmed.indexOf('=');
            props[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim();
          }
        });
        break;
      } catch (_) {}
    }
  }
  return props;
}

const localProps = loadConfigProperties();

// ---------------------------------------------------------------------------
// Product configuration (configurable per assignment requirement)
// ---------------------------------------------------------------------------
const PRODUCT_CONFIG = {
  PERSONAL: {
    minAmount: 50000,
    maxAmount: 500000,
    minTenure: 12,
    maxTenure: 60,
    minMonthlyIncome: 20000,
    annualInterestRate: 12.0
  },
  EDUCATION: {
    minAmount: 100000,
    maxAmount: 1000000,
    minTenure: 12,
    maxTenure: 84,
    minMonthlyIncome: 15000,
    annualInterestRate: 10.0
  }
};

const FOIR_LIMIT = 50.0;       // FOIR must be <= 50%
const MIN_AGE = 21;             // Customer must be at least 21
const MAX_AGE = 60;             // Customer must be no more than 60
const OVERDUE_DAYS = 2;         // Reviews older than 2 calendar days are overdue

// ---------------------------------------------------------------------------
// DB Connection — env vars first, then config.properties, then defaults
// ---------------------------------------------------------------------------
const rawDbUrl = process.env.DB_URL || process.env.DATABASE_URL || process.env.POSTGRES_URL
  || localProps['db.url'] || 'jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require';

const cleanDbUrl = rawDbUrl.replace(/^jdbc:/, '');

let urlUser = '', urlPassword = '';
let dbHost = 'aws-0-ap-southeast-1.pooler.supabase.com';
let dbPort = 5432;
let dbDatabase = 'postgres';

try {
  const urlObj = new URL(cleanDbUrl);
  dbHost     = urlObj.hostname || dbHost;
  dbPort     = parseInt(urlObj.port || '5432', 10);
  dbDatabase = urlObj.pathname.replace(/^\//, '') || 'postgres';
  if (urlObj.username) urlUser     = decodeURIComponent(urlObj.username);
  if (urlObj.password) urlPassword = decodeURIComponent(urlObj.password);
} catch (_) {
  const m = cleanDbUrl.match(/postgresql:\/\/(?:([^:@]+)(?::([^@]+))?@)?([^:/]+)(?::(\d+))?\/([^?]+)/);
  if (m) {
    if (m[1]) urlUser     = decodeURIComponent(m[1]);
    if (m[2]) urlPassword = decodeURIComponent(m[2]);
    dbHost     = m[3];
    dbPort     = parseInt(m[4] || '5432', 10);
    dbDatabase = m[5] || 'postgres';
  }
}

let dbUser     = process.env.DB_USER || process.env.POSTGRES_USER || urlUser || localProps['db.user'] || 'postgres.ciemfotaaupazeqxxuev';
let dbPassword = process.env.DB_PASSWORD || process.env.POSTGRES_PASSWORD || urlPassword || localProps['db.password'] || 'postgres';

const SUPABASE_PROJECT_REF = 'ciemfotaaupazeqxxuev';
if (dbHost.includes('pooler.supabase.com') && dbUser === 'postgres') {
  dbUser = `postgres.${SUPABASE_PROJECT_REF}`;
}

function getFriendlyDbError(err) {
  if (!err || !err.message) return 'Unknown database error';
  if (err.message.includes('password authentication failed')) {
    return `Password authentication failed for user "${dbUser}" on ${dbHost}. Set DB_PASSWORD in Vercel to your real Supabase DB password (Supabase Dashboard → Project Settings → Database → Database Password).`;
  }
  return err.message;
}
function getMaskedDbUrl() {
  return rawDbUrl.replace(/(password=)[^&;]+/i, '$1******');
}

const SUPABASE_CONFIG = {
  url:            process.env.SUPABASE_URL           || localProps['supabase.url']           || `https://${SUPABASE_PROJECT_REF}.supabase.co`,
  businessDate:   process.env.BUSINESS_DATE          || localProps['app.business_date']       || new Date().toISOString().split('T')[0]
};

// ---------------------------------------------------------------------------
// Pool
// ---------------------------------------------------------------------------
let pool = null;
function getPool() {
  if (!pool) {
    pool = new Pool({
      host: dbHost, port: dbPort, database: dbDatabase,
      user: dbUser, password: dbPassword,
      ssl: { rejectUnauthorized: false },
      connectionTimeoutMillis: 5000,
      idleTimeoutMillis: 10000,
      max: 10
    });
    pool.on('error', err => console.error('[PG Pool Error]', err.message));
  }
  return pool;
}

// ---------------------------------------------------------------------------
// RBAC
// ---------------------------------------------------------------------------
const ROLES = {
  LOAN_OFFICER:         ['CUSTOMER_CREATE','CUSTOMER_READ','APPLICATION_DRAFT_CREATE','APPLICATION_DRAFT_EDIT','APPLICATION_SUBMIT','APPLICATION_READ','REPORTS_VIEW'],
  CREDIT_OFFICER:       ['APPLICATION_READ','APPLICATION_REVIEW','CUSTOMER_READ','REPORTS_VIEW','AUDIT_LOG_VIEW'],
  DISBURSEMENT_OFFICER: ['APPLICATION_READ','APPLICATION_DISBURSE','CUSTOMER_READ','REPORTS_VIEW','AUDIT_LOG_VIEW'],
  AUDITOR:              ['CUSTOMER_READ','APPLICATION_READ','REPORTS_VIEW','AUDIT_LOG_VIEW'],
  ADMIN:                ['ALL']
};
function hasPermission(role, perm) {
  if (!role) return false;
  const perms = ROLES[role.toUpperCase()];
  if (!perms) return false;
  return perms.includes('ALL') || perms.includes(perm);
}

// ---------------------------------------------------------------------------
// Financial helpers
// ---------------------------------------------------------------------------
function formatINR(amount) {
  if (amount === undefined || amount === null) return '0.00';
  const num = typeof amount === 'number' ? amount : parseFloat(amount);
  if (isNaN(num)) return '0.00';
  const [intStr, dec] = num.toFixed(2).split('.');
  const last3 = intStr.slice(-3);
  const rest   = intStr.slice(0, -3);
  return (rest !== '' ? rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + ',' : '') + last3 + '.' + dec;
}

function calculateEmi(principal, annualRate, tenureMonths) {
  const r = annualRate / 12 / 100;
  const n = tenureMonths;
  if (r === 0 || n === 0) return 0;
  const factor = Math.pow(1 + r, n);
  return Math.round(((principal * r * factor) / (factor - 1)) * 100) / 100;
}

// ---------------------------------------------------------------------------
// Age calculation (completed years on a given date)
// ---------------------------------------------------------------------------
function completedAge(dob, onDate) {
  const d = new Date(dob);
  const ref = new Date(onDate);
  let age = ref.getFullYear() - d.getFullYear();
  const m = ref.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && ref.getDate() < d.getDate())) age--;
  return age;
}

// ---------------------------------------------------------------------------
// Eligibility evaluation (run at submit time — returns all failed rules)
// ---------------------------------------------------------------------------
function evaluateEligibility(app, customerDob, businessDate) {
  const cfg     = PRODUCT_CONFIG[app.product] || PRODUCT_CONFIG.PERSONAL;
  const P       = parseFloat(app.requested_amount);
  const n       = parseInt(app.tenure_months, 10);
  const income  = parseFloat(app.monthly_income);
  const oblig   = parseFloat(app.existing_monthly_obligations || 0);
  const rate    = cfg.annualInterestRate;
  const emi     = calculateEmi(P, rate, n);
  const foir    = Math.round(((oblig + emi) / income * 100) * 100) / 100;
  const age     = completedAge(customerDob, businessDate);

  const failed = [];

  // Customer age
  if (age < MIN_AGE) failed.push(`Customer age ${age} years is below minimum ${MIN_AGE} years.`);
  if (age > MAX_AGE) failed.push(`Customer age ${age} years exceeds maximum ${MAX_AGE} years.`);

  // Future DOB (double-check)
  if (new Date(customerDob) > new Date(businessDate)) failed.push('Date of birth cannot be a future date.');

  // Income minimum
  if (income < cfg.minMonthlyIncome) failed.push(`Monthly net income INR ${formatINR(income)} is below minimum INR ${formatINR(cfg.minMonthlyIncome)} for ${app.product}.`);

  // Amount range
  if (P < cfg.minAmount || P > cfg.maxAmount) failed.push(`Requested amount INR ${formatINR(P)} must be between INR ${formatINR(cfg.minAmount)} and INR ${formatINR(cfg.maxAmount)} for ${app.product}.`);

  // Tenure range
  if (n < cfg.minTenure || n > cfg.maxTenure) failed.push(`Tenure ${n} months must be between ${cfg.minTenure} and ${cfg.maxTenure} months for ${app.product}.`);

  // Income positive
  if (income <= 0) failed.push('Monthly net income must be positive.');

  // FOIR
  if (foir > FOIR_LIMIT) failed.push(`FOIR ${foir.toFixed(2)}% exceeds maximum ${FOIR_LIMIT}% (EMI INR ${formatINR(emi)} + obligations INR ${formatINR(oblig)} = INR ${formatINR(emi + oblig)} on income INR ${formatINR(income)}).`);

  return { eligible: failed.length === 0, failedRules: failed, emi, foir, rate };
}

// ---------------------------------------------------------------------------
// Row mappers (DB → API)
// ---------------------------------------------------------------------------
function mapCustomer(row) {
  if (!row) return null;
  return {
    customerId:  row.customer_id,
    name:        row.name,
    dateOfBirth: row.date_of_birth instanceof Date ? row.date_of_birth.toISOString().split('T')[0] : String(row.date_of_birth || ''),
    mobile:      row.mobile,
    address:     row.address,
    kycDetails:  row.kyc_details,
    createdAt:   row.created_at
  };
}

function mapApplication(row) {
  if (!row) return null;
  return {
    applicationId:              parseInt(row.application_id, 10),
    applicationNumber:          row.application_number,
    customerId:                 row.customer_id,
    customerName:               row.customer_name || row.customer_id,
    product:                    row.product,
    branchCode:                 row.branch_code,
    branchName:                 row.branch_name,
    requestedAmount:            parseFloat(row.requested_amount),
    tenureMonths:               parseInt(row.tenure_months, 10),
    monthlyIncome:              parseFloat(row.monthly_income),
    existingMonthlyObligations: parseFloat(row.existing_monthly_obligations || 0),
    annualInterestRate:         parseFloat(row.annual_interest_rate),
    proposedEmi:                parseFloat(row.proposed_emi),
    foir:                       parseFloat(row.foir_percentage),
    foirPercentage:             parseFloat(row.foir_percentage),
    eligible:                   Boolean(row.eligible),
    eligibilityReasons:         row.eligibility_reasons,
    status:                     row.status,
    createdAt:                  row.created_at,
    submittedAt:                row.submitted_at instanceof Date ? row.submitted_at.toISOString().split('T')[0] : (row.submitted_at || null),
    decidedAt:                  row.decided_at,
    reviewerCode:               row.reviewer_code,
    decisionReason:             row.decision_reason
  };
}

// ---------------------------------------------------------------------------
// Body parser
// ---------------------------------------------------------------------------
async function parseBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  return new Promise(resolve => {
    let raw = '';
    req.on('data', chunk => { raw += chunk.toString(); });
    req.on('end', () => {
      try { resolve(JSON.parse(raw)); } catch (_) { resolve({}); }
    });
  });
}

// ---------------------------------------------------------------------------
// Schema initialisation — idempotent, run once per cold-start
// ---------------------------------------------------------------------------
let schemaInitialized = false;
async function ensureSchema(p) {
  if (schemaInitialized) return;
  const ddl = `
    CREATE TABLE IF NOT EXISTS customer (
      customer_id  VARCHAR(15) PRIMARY KEY,
      name         VARCHAR(100) NOT NULL,
      date_of_birth DATE NOT NULL,
      mobile       VARCHAR(10)  UNIQUE NOT NULL,
      address      TEXT         NOT NULL,
      kyc_details  TEXT         NOT NULL,
      created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS loan_application (
      application_id               BIGSERIAL PRIMARY KEY,
      application_number           VARCHAR(30) UNIQUE NOT NULL,
      customer_id                  VARCHAR(15) NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
      product                      VARCHAR(20) NOT NULL CHECK (product IN ('PERSONAL','EDUCATION')),
      branch_code                  VARCHAR(20) NOT NULL DEFAULT 'BR001',
      branch_name                  VARCHAR(100) NOT NULL DEFAULT 'Main Branch',
      requested_amount             NUMERIC(15,2) NOT NULL,
      tenure_months                INT NOT NULL,
      monthly_income               NUMERIC(15,2) NOT NULL,
      existing_monthly_obligations NUMERIC(15,2) NOT NULL DEFAULT 0,
      annual_interest_rate         NUMERIC(5,2)  NOT NULL,
      proposed_emi                 NUMERIC(15,2) NOT NULL DEFAULT 0,
      foir_percentage              NUMERIC(5,2)  NOT NULL DEFAULT 0,
      eligible                     BOOLEAN       NOT NULL DEFAULT FALSE,
      eligibility_reasons          TEXT,
      status                       VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
      created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      submitted_at                 TIMESTAMP WITH TIME ZONE,
      decided_at                   TIMESTAMP WITH TIME ZONE,
      reviewer_code                VARCHAR(50),
      decision_reason              TEXT
    );

    CREATE TABLE IF NOT EXISTS disbursement (
      disbursement_id  BIGSERIAL PRIMARY KEY,
      application_id   BIGINT  UNIQUE NOT NULL REFERENCES loan_application(application_id) ON DELETE RESTRICT,
      reference        VARCHAR(50) UNIQUE NOT NULL,
      amount           NUMERIC(15,2) NOT NULL,
      disbursed_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS application_history (
      history_id    BIGSERIAL PRIMARY KEY,
      application_id BIGINT NOT NULL REFERENCES loan_application(application_id) ON DELETE CASCADE,
      action        VARCHAR(50) NOT NULL,
      old_status    VARCHAR(30),
      new_status    VARCHAR(30) NOT NULL,
      performed_by  VARCHAR(50) NOT NULL,
      performed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      remarks       TEXT
    );

    CREATE TABLE IF NOT EXISTS system_alerts (
      alert_id       BIGSERIAL PRIMARY KEY,
      application_id BIGINT NOT NULL REFERENCES loan_application(application_id) ON DELETE CASCADE,
      alert_type     VARCHAR(50) NOT NULL,
      created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT uq_system_alerts_app_type UNIQUE (application_id, alert_type)
    );

    CREATE TABLE IF NOT EXISTS utility_run (
      run_id                 BIGSERIAL PRIMARY KEY,
      utility_name           VARCHAR(50) NOT NULL DEFAULT 'OVERDUE_MONITOR',
      started_at             TIMESTAMP WITH TIME ZONE NOT NULL,
      completed_at           TIMESTAMP WITH TIME ZONE,
      result                 VARCHAR(30) NOT NULL,
      applications_processed INT NOT NULL DEFAULT 0,
      alerts_created         INT NOT NULL DEFAULT 0,
      error_message          TEXT
    );

    CREATE TABLE IF NOT EXISTS ng_core_banking (
      core_id          BIGSERIAL PRIMARY KEY,
      application_id   BIGINT  UNIQUE NOT NULL REFERENCES loan_application(application_id) ON DELETE RESTRICT,
      customer_id      VARCHAR(15) NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
      customer_name    VARCHAR(100) NOT NULL,
      address          TEXT NOT NULL,
      dob              DATE NOT NULL,
      kyc_details      TEXT NOT NULL,
      approved_amount  NUMERIC(15,2) NOT NULL,
      disbursed_amount NUMERIC(15,2) NOT NULL,
      disbursed_date   DATE NOT NULL,
      synced_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS app_user (
      user_id     BIGSERIAL PRIMARY KEY,
      username    VARCHAR(50) UNIQUE NOT NULL,
      full_name   VARCHAR(100) NOT NULL,
      role        VARCHAR(30) NOT NULL,
      email       VARCHAR(100) UNIQUE NOT NULL,
      branch_code VARCHAR(20) DEFAULT 'BR001',
      created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
  `;
  const migrations = [
    `ALTER TABLE disbursement ADD COLUMN IF NOT EXISTS reference VARCHAR(50);`,
    `ALTER TABLE disbursement ADD COLUMN IF NOT EXISTS amount NUMERIC(15,2);`,
    `DO $$ BEGIN
      IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='disbursement' AND column_name='disbursed_amount') THEN
        ALTER TABLE disbursement ALTER COLUMN disbursed_amount DROP NOT NULL;
      END IF;
      IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='disbursement' AND column_name='disbursement_reference') THEN
        ALTER TABLE disbursement ALTER COLUMN disbursement_reference DROP NOT NULL;
      END IF;
    END $$;`,
    `DO $$ BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='disbursement_reference_key' AND conrelid='disbursement'::regclass) THEN
        ALTER TABLE disbursement ADD CONSTRAINT disbursement_reference_key UNIQUE (reference);
      END IF;
    END $$;`,
    `DO $$ BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='disbursement_application_id_key' AND conrelid='disbursement'::regclass) THEN
        ALTER TABLE disbursement ADD CONSTRAINT disbursement_application_id_key UNIQUE (application_id);
      END IF;
    END $$;`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS utility_name VARCHAR(50) NOT NULL DEFAULT 'OVERDUE_MONITOR';`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS started_at TIMESTAMP WITH TIME ZONE;`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS result VARCHAR(30);`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS applications_processed INT NOT NULL DEFAULT 0;`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS alerts_created INT NOT NULL DEFAULT 0;`,
    `ALTER TABLE utility_run ADD COLUMN IF NOT EXISTS error_message TEXT;`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS customer_id VARCHAR(15);`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS customer_name VARCHAR(100);`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS address TEXT;`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS dob DATE;`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS kyc_details TEXT;`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS approved_amount NUMERIC(15,2);`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS disbursed_amount NUMERIC(15,2);`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS disbursed_date DATE;`,
    `ALTER TABLE ng_core_banking ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;`,
    `DO $$
    DECLARE col RECORD;
    BEGIN
      FOR col IN
        SELECT column_name FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ng_core_banking' AND is_nullable = 'NO'
          AND column_name NOT IN ('core_id','id','application_id','customer_id','customer_name','address','dob','kyc_details','approved_amount','disbursed_amount','disbursed_date','synced_at')
      LOOP
        EXECUTE format('ALTER TABLE ng_core_banking ALTER COLUMN %I DROP NOT NULL', col.column_name);
      END LOOP;
    END $$;`,
    `DO $$ BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ng_core_banking_application_id_key' AND conrelid='ng_core_banking'::regclass) THEN
        ALTER TABLE ng_core_banking ADD CONSTRAINT ng_core_banking_application_id_key UNIQUE (application_id);
      END IF;
    END $$;`,
    `DO $$ BEGIN
      IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_system_alerts_app_type' AND conrelid='system_alerts'::regclass) THEN
        ALTER TABLE system_alerts ADD CONSTRAINT uq_system_alerts_app_type UNIQUE (application_id, alert_type);
      END IF;
    END $$;`,
    `INSERT INTO app_user (username, full_name, role, email, branch_code) VALUES
      ('officer_ramesh', 'Ramesh Sharma',       'LOAN_OFFICER',         'ramesh@losbank.in', 'BR001'),
      ('credit_priya',   'Priya Iyer',           'CREDIT_OFFICER',       'priya@losbank.in',  'BR001'),
      ('treasury_amit',  'Amit Patel',           'DISBURSEMENT_OFFICER', 'amit@losbank.in',   'BR001'),
      ('admin_super',    'Rajesh Gupta (Admin)', 'ADMIN',                'admin@losbank.in',  'HQ001'),
      ('auditor_neha',   'Neha Kulkarni',        'AUDITOR',              'neha@losbank.in',   'AUDIT01')
    ON CONFLICT (username) DO NOTHING;`
  ];

  try {
    await p.query(ddl);
  } catch (err) {
    console.error('[Schema Init Warning]', err.message);
  }
  for (const m of migrations) {
    try {
      await p.query(m);
    } catch (err) {
      console.error('[Migration Warning]', err.message);
    }
  }
  schemaInitialized = true;
}

// ===========================================================================
// MAIN HANDLER
// ===========================================================================
export default async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-User-Role, X-User-Name, X-Business-Date');
  if (req.method === 'OPTIONS') return res.status(204).end();

  const rawUrl  = req.url || '';
  const urlObj  = new URL(rawUrl, 'http://localhost');
  const pathname = urlObj.pathname;
  const query   = urlObj.searchParams;
  const method  = req.method;

  const userRole = (req.headers['x-user-role'] || 'ADMIN').toUpperCase();
  const userName = req.headers['x-user-name'] || 'admin_super';
  const businessDate = (req.headers['x-business-date'] && /^\d{4}-\d{2}-\d{2}$/.test(req.headers['x-business-date']))
    ? req.headers['x-business-date']
    : SUPABASE_CONFIG.businessDate;

  // =========================================================================
  // 1. HEALTH CHECK
  // =========================================================================
  if (pathname === '/api/health') {
    try {
      const p = getPool();
      await ensureSchema(p);
      const r = await p.query('SELECT version() AS v, current_database() AS db, current_user AS u;');
      const ver = r.rows[0]?.v || 'PostgreSQL';
      return res.status(200).json({
        status: 'UP',
        environment: 'Vercel Serverless',
        database: { status: 'CONNECTED', product: ver.split(' ')[0], version: ver, host: dbHost, url: getMaskedDbUrl(), user: dbUser, connected: true },
        businessDate: SUPABASE_CONFIG.businessDate,
        productConfig: PRODUCT_CONFIG
      });
    } catch (err) {
      return res.status(503).json({ status: 'DOWN', database: { status: 'DISCONNECTED', host: dbHost, user: dbUser, error: getFriendlyDbError(err) } });
    }
  }

  const p = getPool();
  await ensureSchema(p);

  // =========================================================================
  // 2. USERS
  // =========================================================================
  if (pathname === '/api/users' && method === 'GET') {
    try {
      const r = await p.query('SELECT user_id, username, full_name, role, email, branch_code FROM app_user ORDER BY user_id;');
      return res.status(200).json(r.rows.map(row => ({
        userId: row.user_id, username: row.username, fullName: row.full_name,
        role: row.role, email: row.email, branchCode: row.branch_code
      })));
    } catch (err) {
      return res.status(500).json({ error: 'DB error fetching users: ' + getFriendlyDbError(err) });
    }
  }

  // =========================================================================
  // 3. CUSTOMERS — create, search, list
  // =========================================================================
  if (pathname === '/api/customers') {

    // ---- GET ----
    if (method === 'GET') {
      if (!hasPermission(userRole, 'CUSTOMER_READ'))
        return res.status(403).json({ error: `Access Denied: ${userRole} cannot read customers.` });
      try {
        const search = query.get('search') || '';
        let sql = 'SELECT * FROM customer';
        const params = [];
        if (search) {
          sql += ' WHERE customer_id ILIKE $1 OR name ILIKE $1 OR mobile ILIKE $1';
          params.push(`%${search}%`);
        }
        sql += ' ORDER BY created_at DESC;';
        const r = await p.query(sql, params);
        return res.status(200).json(r.rows.map(mapCustomer));
      } catch (err) {
        return res.status(500).json({ error: 'Database error fetching customers: ' + getFriendlyDbError(err) });
      }
    }

    // ---- POST: Create customer ----
    if (method === 'POST') {
      if (!hasPermission(userRole, 'CUSTOMER_CREATE'))
        return res.status(403).json({ error: `Access Denied: ${userRole} cannot create customers.` });

      const body = await parseBody(req);
      const errors = [];

      // Required fields
      if (!body.name || !String(body.name).trim())       errors.push('Name is required.');
      if (!body.mobile)                                   errors.push('Mobile is required.');
      if (!body.dateOfBirth)                             errors.push('Date of birth is required.');
      if (!body.address || !String(body.address).trim()) errors.push('Address is required.');
      if (!body.kycDetails)                              errors.push('KYC details are required.');
      if (errors.length) return res.status(400).json({ error: errors.join(' ') });

      // Mobile: exactly 10 digits, unique
      if (!/^\d{10}$/.test(String(body.mobile).trim()))
        return res.status(400).json({ error: 'Mobile number must contain exactly 10 digits.' });

      // Reject future DOB
      const dobDate = new Date(body.dateOfBirth);
      if (isNaN(dobDate.getTime()))
        return res.status(400).json({ error: 'Invalid date of birth.' });
      if (dobDate > new Date(SUPABASE_CONFIG.businessDate))
        return res.status(400).json({ error: 'Date of birth cannot be a future date.' });

      try {
        const dup = await p.query('SELECT 1 FROM customer WHERE mobile = $1;', [String(body.mobile).trim()]);
        if (dup.rows.length > 0)
          return res.status(409).json({ error: `Mobile ${body.mobile} is already registered.` });

        const customerId = 'CUST-' + Date.now().toString(36).toUpperCase().slice(-6);
        const r = await p.query(
          `INSERT INTO customer (customer_id, name, date_of_birth, mobile, address, kyc_details, created_at)
           VALUES ($1,$2,$3,$4,$5,$6,NOW()) RETURNING *;`,
          [customerId, String(body.name).trim(), body.dateOfBirth, String(body.mobile).trim(),
           String(body.address).trim(), String(body.kycDetails).trim()]
        );
        return res.status(201).json(mapCustomer(r.rows[0]));
      } catch (err) {
        return res.status(500).json({ error: 'Failed to create customer: ' + getFriendlyDbError(err) });
      }
    }
  }

  // =========================================================================
  // 4. APPLICATIONS — list, create draft
  // =========================================================================
  if (pathname === '/api/applications') {

    // ---- GET: list applications ----
    if (method === 'GET') {
      if (!hasPermission(userRole, 'APPLICATION_READ'))
        return res.status(403).json({ error: `Access Denied: ${userRole} cannot read applications.` });
      try {
        const r = await p.query(`
          SELECT a.*, c.name AS customer_name
          FROM   loan_application a
          LEFT JOIN customer c ON a.customer_id = c.customer_id
          ORDER BY a.created_at DESC;
        `);
        return res.status(200).json(r.rows.map(mapApplication));
      } catch (err) {
        return res.status(500).json({ error: 'Database error fetching applications: ' + getFriendlyDbError(err) });
      }
    }

    // ---- POST: create DRAFT ----
    if (method === 'POST') {
      if (!hasPermission(userRole, 'APPLICATION_DRAFT_CREATE'))
        return res.status(403).json({ error: `Access Denied: ${userRole} cannot create applications.` });

      const body = await parseBody(req);
      if (!body.customerId || !body.product || !body.requestedAmount || !body.tenureMonths || !body.monthlyIncome)
        return res.status(400).json({ error: 'Missing required fields: customerId, product, requestedAmount, tenureMonths, monthlyIncome.' });

      const product   = String(body.product).toUpperCase();
      if (!PRODUCT_CONFIG[product])
        return res.status(400).json({ error: `Invalid product "${product}". Must be PERSONAL or EDUCATION.` });

      const cfg    = PRODUCT_CONFIG[product];
      const P      = parseFloat(body.requestedAmount);
      const rawN   = Number(body.tenureMonths);
      const n      = parseInt(body.tenureMonths, 10);
      const income = parseFloat(body.monthlyIncome);
      const oblig  = parseFloat(body.existingMonthlyObligations || 0);

      // Input sanity validation (positive income, nonnegative obligations, whole-number tenure)
      const inputErrors = [];
      if (isNaN(P) || P <= 0)
        inputErrors.push('Requested amount must be positive.');
      if (isNaN(rawN) || !Number.isInteger(rawN) || n <= 0)
        inputErrors.push('Tenure must be a positive whole number.');
      if (isNaN(income) || income <= 0)
        inputErrors.push('Monthly income must be positive.');
      if (isNaN(oblig) || oblig < 0)
        inputErrors.push('Existing monthly obligations cannot be negative.');
      if (inputErrors.length) return res.status(400).json({ error: inputErrors.join(' ') });

      const appNumber = 'APP-' + new Date().getFullYear() + '-' + String(Date.now()).slice(-6);
      const client = await p.connect();
      try {
        await client.query('BEGIN');

        const custRes = await client.query('SELECT name, date_of_birth FROM customer WHERE customer_id = $1;', [body.customerId]);
        if (custRes.rows.length === 0) {
          await client.query('ROLLBACK');
          return res.status(404).json({ error: `Customer not found: ${body.customerId}` });
        }

        // Active-application check (DRAFT / UNDER_REVIEW / CREDIT_APPROVAL)
        const activeRes = await client.query(
          `SELECT 1 FROM loan_application WHERE customer_id=$1 AND product=$2 AND status IN ('DRAFT','UNDER_REVIEW','CREDIT_APPROVAL');`,
          [body.customerId, product]
        );
        if (activeRes.rows.length > 0) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: `Customer already has an active ${product} application.` });
        }

        const evalRes = evaluateEligibility(
          { product, requested_amount: P, tenure_months: n, monthly_income: income, existing_monthly_obligations: oblig },
          custRes.rows[0].date_of_birth,
          businessDate
        );

        const r = await client.query(`
          INSERT INTO loan_application (
            application_number, customer_id, product, branch_code, branch_name,
            requested_amount, tenure_months, monthly_income, existing_monthly_obligations,
            annual_interest_rate, proposed_emi, foir_percentage, eligible, eligibility_reasons,
            status, created_at
          ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,'DRAFT',NOW())
          RETURNING *;`,
          [appNumber, body.customerId, product,
           body.branchCode || 'BR001', body.branchName || 'Main Branch',
           P, n, income, oblig, evalRes.rate, evalRes.emi, evalRes.foir,
           evalRes.eligible,
           evalRes.eligible ? 'Draft created — all preliminary rules met.' : evalRes.failedRules.join(' | ')
          ]
        );

        await client.query(
          `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
           VALUES ($1,'CREATE_DRAFT','NONE','DRAFT',$2,NOW(),'Draft created via web');`,
          [r.rows[0].application_id, userName]
        );

        await client.query('COMMIT');
        const newApp = r.rows[0];
        newApp.customer_name = custRes.rows[0].name;
        return res.status(201).json(mapApplication(newApp));
      } catch (err) {
        await client.query('ROLLBACK');
        return res.status(500).json({ error: 'Failed to create application: ' + err.message });
      } finally {
        client.release();
      }
    }
  }

  // =========================================================================
  // 4b. EDIT APPLICATION DRAFT (Allowed ONLY in DRAFT status)
  // =========================================================================
  const editMatch = pathname.match(/^\/api\/applications\/(\d+)$/);
  if (editMatch && method === 'PUT') {
    if (!hasPermission(userRole, 'APPLICATION_DRAFT_EDIT'))
      return res.status(403).json({ error: `Access Denied: ${userRole} cannot edit applications.` });

    const appId = parseInt(editMatch[1], 10);
    const body  = await parseBody(req);
    const client = await p.connect();
    try {
      await client.query('BEGIN');

      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id=$1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'DRAFT') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Editing is allowed only in DRAFT status. Current status: ${app.status}.` });
      }

      const product = body.product ? String(body.product).toUpperCase() : app.product;
      if (!PRODUCT_CONFIG[product]) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Invalid product "${product}". Must be PERSONAL or EDUCATION.` });
      }

      const P      = body.requestedAmount !== undefined ? parseFloat(body.requestedAmount) : parseFloat(app.requested_amount);
      const rawN   = body.tenureMonths !== undefined ? Number(body.tenureMonths) : Number(app.tenure_months);
      const n      = body.tenureMonths !== undefined ? parseInt(body.tenureMonths, 10) : parseInt(app.tenure_months, 10);
      const income = body.monthlyIncome !== undefined ? parseFloat(body.monthlyIncome) : parseFloat(app.monthly_income);
      const oblig  = body.existingMonthlyObligations !== undefined ? parseFloat(body.existingMonthlyObligations) : parseFloat(app.existing_monthly_obligations || 0);
      const bCode  = body.branchCode !== undefined ? String(body.branchCode).trim() : app.branch_code;
      const bName  = body.branchName !== undefined ? String(body.branchName).trim() : app.branch_name;

      const inputErrors = [];
      if (isNaN(P) || P <= 0) inputErrors.push('Requested amount must be positive.');
      if (isNaN(rawN) || !Number.isInteger(rawN) || n <= 0) inputErrors.push('Tenure must be a positive whole number.');
      if (isNaN(income) || income <= 0) inputErrors.push('Monthly income must be positive.');
      if (isNaN(oblig) || oblig < 0) inputErrors.push('Existing monthly obligations cannot be negative.');
      if (inputErrors.length) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: inputErrors.join(' ') });
      }

      // If product changed, check active-application uniqueness for new product
      if (product !== app.product) {
        const activeRes = await client.query(
          `SELECT 1 FROM loan_application WHERE customer_id=$1 AND product=$2 AND application_id<>$3 AND status IN ('DRAFT','UNDER_REVIEW','CREDIT_APPROVAL');`,
          [app.customer_id, product, appId]
        );
        if (activeRes.rows.length > 0) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: `Customer already has an active ${product} application.` });
        }
      }

      const custRes = await client.query('SELECT name, date_of_birth FROM customer WHERE customer_id=$1;', [app.customer_id]);
      const evalRes = evaluateEligibility(
        { product, requested_amount: P, tenure_months: n, monthly_income: income, existing_monthly_obligations: oblig },
        custRes.rows[0]?.date_of_birth,
        businessDate
      );

      const upd = await client.query(`
        UPDATE loan_application
        SET product=$1, branch_code=$2, branch_name=$3, requested_amount=$4, tenure_months=$5,
            monthly_income=$6, existing_monthly_obligations=$7, annual_interest_rate=$8,
            proposed_emi=$9, foir_percentage=$10, eligible=$11, eligibility_reasons=$12
        WHERE application_id=$13
        RETURNING *;`,
        [
          product, bCode || 'BR001', bName || 'Main Branch', P, n, income, oblig,
          evalRes.rate, evalRes.emi, evalRes.foir, evalRes.eligible,
          evalRes.eligible ? 'Draft updated — all preliminary rules met.' : evalRes.failedRules.join(' | '),
          appId
        ]
      );

      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1,'EDIT_DRAFT','DRAFT','DRAFT',$2,NOW(),'Draft financial/loan details updated');`,
        [appId, userName]
      );

      await client.query('COMMIT');
      const updated = upd.rows[0];
      updated.customer_name = custRes.rows[0]?.name;
      return res.status(200).json(mapApplication(updated));
    } catch (err) {
      await client.query('ROLLBACK');
      return res.status(500).json({ error: 'Failed to update draft: ' + err.message });
    } finally {
      client.release();
    }
  }

  // =========================================================================
  // 5. SUBMIT APPLICATION — full eligibility re-evaluation, all failed rules
  // =========================================================================
  const submitMatch = pathname.match(/^\/api\/applications\/(\d+)\/submit$/);
  if (submitMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_SUBMIT'))
      return res.status(403).json({ error: `Access Denied: ${userRole} cannot submit applications.` });

    const appId  = parseInt(submitMatch[1], 10);
    const client = await p.connect();
    try {
      await client.query('BEGIN');

      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id=$1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'DRAFT') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Cannot submit: status is ${app.status}, must be DRAFT.` });
      }

      // Fetch customer DOB for age validation
      const custRes = await client.query('SELECT date_of_birth FROM customer WHERE customer_id=$1;', [app.customer_id]);
      const customerDob = custRes.rows[0]?.date_of_birth;

      // Full eligibility evaluation
      const { eligible, failedRules, emi, foir, rate } = evaluateEligibility(app, customerDob, businessDate);

      const newStatus = eligible ? 'UNDER_REVIEW' : 'REJECTED';
      const reasons   = eligible
        ? 'All eligibility criteria met.'
        : failedRules.join(' | ');

      await client.query(
        `UPDATE loan_application
         SET status=$1, submitted_at=$2, proposed_emi=$3, foir_percentage=$4,
             annual_interest_rate=$5, eligible=$6, eligibility_reasons=$7
         WHERE application_id=$8;`,
        [newStatus, businessDate, emi, foir, rate, eligible, reasons, appId]
      );

      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1,'SUBMIT','DRAFT',$2,$3,NOW(),$4);`,
        [appId, newStatus, userName, reasons]
      );

      await client.query('COMMIT');
      const updated = await p.query(
        'SELECT a.*, c.name AS customer_name FROM loan_application a LEFT JOIN customer c ON a.customer_id=c.customer_id WHERE a.application_id=$1;',
        [appId]
      );
      return res.status(200).json({
        ...mapApplication(updated.rows[0]),
        allFailedRules: failedRules,
        eligible
      });
    } catch (err) {
      await client.query('ROLLBACK');
      return res.status(500).json({ error: 'Failed to submit application: ' + err.message });
    } finally {
      client.release();
    }
  }

  // =========================================================================
  // 6. REVIEW APPLICATION (UNDER_REVIEW → CREDIT_APPROVAL or REJECTED)
  // =========================================================================
  const reviewMatch = pathname.match(/^\/api\/applications\/(\d+)\/review$/);
  if (reviewMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_REVIEW'))
      return res.status(403).json({ error: `Access Denied: ${userRole} cannot review applications.` });

    const appId  = parseInt(reviewMatch[1], 10);
    const body   = await parseBody(req);
    const approve = Boolean(body.approve);

    if (!approve && (!body.decisionReason || !String(body.decisionReason).trim()))
      return res.status(400).json({ error: 'Rejection requires a decision reason.' });

    const reviewerCode = body.reviewerCode || userName;
    const reason       = body.decisionReason || (approve ? 'Approved by Credit Committee' : 'Adverse parameters');

    const client = await p.connect();
    try {
      await client.query('BEGIN');

      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id=$1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'UNDER_REVIEW') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Cannot review from status ${app.status}. Must be UNDER_REVIEW.` });
      }

      const newStatus = approve ? 'CREDIT_APPROVAL' : 'REJECTED';
      await client.query(
        `UPDATE loan_application SET status=$1, reviewer_code=$2, decision_reason=$3, decided_at=NOW() WHERE application_id=$4;`,
        [newStatus, reviewerCode, reason, appId]
      );
      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1,$2,'UNDER_REVIEW',$3,$4,NOW(),$5);`,
        [appId, approve ? 'APPROVE' : 'REJECT', newStatus, userName, reason]
      );

      await client.query('COMMIT');
      const updated = await p.query(
        'SELECT a.*, c.name AS customer_name FROM loan_application a LEFT JOIN customer c ON a.customer_id=c.customer_id WHERE a.application_id=$1;',
        [appId]
      );
      return res.status(200).json(mapApplication(updated.rows[0]));
    } catch (err) {
      await client.query('ROLLBACK');
      return res.status(500).json({ error: 'Failed to record review: ' + err.message });
    } finally {
      client.release();
    }
  }

  // =========================================================================
  // 7. DISBURSE APPLICATION (CREDIT_APPROVAL → DISBURSED, atomic)
  // =========================================================================
  const disburseMatch = pathname.match(/^\/api\/applications\/(\d+)\/disburse$/);
  if (disburseMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_DISBURSE'))
      return res.status(403).json({ error: `Access Denied: ${userRole} cannot disburse applications.` });

    const appId  = parseInt(disburseMatch[1], 10);
    const body   = await parseBody(req);
    const customRef = body && body.reference ? String(body.reference).trim() : '';
    const client = await p.connect();
    try {
      await client.query('BEGIN');

      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id=$1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'CREDIT_APPROVAL' && app.status !== 'APPROVED') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Cannot disburse from status ${app.status}. Must be CREDIT_APPROVAL.` });
      }

      // Idempotency guard: prevent duplicate disbursement
      const dupCheck = await client.query('SELECT 1 FROM disbursement WHERE application_id=$1;', [appId]);
      if (dupCheck.rows.length > 0) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'Application has already been disbursed.' });
      }

      const amount = parseFloat(app.requested_amount);
      const ref    = customRef || ('DISB-' + Date.now() + '-' + Math.floor(Math.random() * 10000));

      // Check duplicate reference explicitly
      const refDup = await client.query('SELECT 1 FROM disbursement WHERE reference=$1;', [ref]);
      if (refDup.rows.length > 0) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: `Duplicate disbursement reference "${ref}". Reference must be globally unique.` });
      }

      await client.query(
        `INSERT INTO disbursement (application_id, reference, amount, disbursed_at) VALUES ($1,$2,$3,NOW());`,
        [appId, ref, amount]
      );
      await client.query(
        `UPDATE loan_application SET status='DISBURSED' WHERE application_id=$1;`,
        [appId]
      );
      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1,'DISBURSE',$2,'DISBURSED',$3,NOW(),$4);`,
        [appId, app.status, userName, `Disbursed INR ${formatINR(amount)}. Ref: ${ref}`]
      );

      await client.query('COMMIT');

      // Return confirmation with customer + application details
      const custRes = await p.query('SELECT name FROM customer WHERE customer_id=$1;', [app.customer_id]);
      return res.status(200).json({
        applicationId:     appId,
        applicationNumber: app.application_number,
        customer:          custRes.rows[0]?.name || app.customer_id,
        amount:            amount,
        formattedAmount:   'INR ' + formatINR(amount),
        reference:         ref,
        date:              businessDate,
        status:            'DISBURSED'
      });
    } catch (err) {
      await client.query('ROLLBACK');
      if (err && err.code === '23505') {
        return res.status(409).json({ error: 'Duplicate disbursement or duplicate reference rejected by database constraint.' });
      }
      return res.status(500).json({ error: 'Failed to disburse application: ' + err.message });
    } finally {
      client.release();
    }
  }

  // =========================================================================
  // 8. APPLICATION HISTORY
  // =========================================================================
  const historyMatch = pathname.match(/^\/api\/applications\/(\d+)\/history$/);
  if (historyMatch && method === 'GET') {
    const appId = parseInt(historyMatch[1], 10);
    try {
      const r = await p.query(
        'SELECT * FROM application_history WHERE application_id=$1 ORDER BY performed_at ASC;',
        [appId]
      );
      return res.status(200).json(r.rows.map(row => ({
        historyId:    parseInt(row.history_id, 10),
        applicationId: parseInt(row.application_id, 10),
        action:       row.action,
        oldStatus:    row.old_status,
        newStatus:    row.new_status,
        performedBy:  row.performed_by,
        performedAt:  row.performed_at,
        remarks:      row.remarks
      })));
    } catch (err) {
      return res.status(500).json({ error: 'DB error fetching history: ' + err.message });
    }
  }

  // =========================================================================
  // 9. SIX SQL REPORTS
  // =========================================================================
  if (pathname.startsWith('/api/reports/')) {
    if (!hasPermission(userRole, 'REPORTS_VIEW'))
      return res.status(403).json({ error: `Access Denied: ${userRole} cannot view reports.` });

    try {
      // ----- Report 1: Application register (filter by product, status) -----
      if (pathname === '/api/reports/register') {
        const productFilter = query.get('product') || '';
        const statusFilter  = query.get('status')  || '';
        let sql = `
          SELECT a.application_number, c.name AS customer_name, a.customer_id, a.product,
                 a.branch_code, a.branch_name, a.requested_amount, a.proposed_emi,
                 a.foir_percentage, a.status, a.submitted_at
          FROM loan_application a
          LEFT JOIN customer c ON a.customer_id = c.customer_id
          WHERE 1=1`;
        const params = [];
        if (productFilter) { params.push(productFilter.toUpperCase()); sql += ` AND a.product=$${params.length}`; }
        if (statusFilter)  { params.push(statusFilter.toUpperCase());  sql += ` AND a.status=$${params.length}`; }
        sql += ' ORDER BY a.created_at DESC;';
        const r = await p.query(sql, params);
        return res.status(200).json(r.rows.map(a => ({
          applicationNumber: a.application_number,
          customerName:      a.customer_name || a.customer_id,
          product:           a.product,
          branchCode:        a.branch_code,
          branchName:        a.branch_name,
          formattedRequestedAmount: 'INR ' + formatINR(a.requested_amount),
          formattedProposedEmi:     'INR ' + formatINR(a.proposed_emi),
          foirPercentage:    parseFloat(a.foir_percentage),
          status:            a.status,
          submittedAt:       a.submitted_at instanceof Date ? a.submitted_at.toISOString().split('T')[0] : (a.submitted_at || '-')
        })));
      }

      // ----- Report 2a: Product pipeline (by product + status) -----
      if (pathname === '/api/reports/pipeline/product') {
        const r = await p.query(`
          SELECT product, status, COUNT(*) AS count, SUM(requested_amount) AS total_amount
          FROM loan_application
          GROUP BY product, status
          ORDER BY product, status;
        `);
        return res.status(200).json(r.rows.map(row => ({
          key1:              row.product,
          key2:              row.status,
          product:           row.product,
          status:            row.status,
          totalApplications: parseInt(row.count, 10),
          totalAmount:       parseFloat(row.total_amount || 0),
          formattedAmount:   'INR ' + formatINR(row.total_amount || 0)
        })));
      }

      // ----- Report 2b: Pipeline by branch + product -----
      if (pathname === '/api/reports/pipeline/branch') {
        const r = await p.query(`
          SELECT branch_code, branch_name, product, COUNT(*) AS count, SUM(requested_amount) AS total_amount
          FROM loan_application
          GROUP BY branch_code, branch_name, product
          ORDER BY branch_code, product;
        `);
        return res.status(200).json(r.rows.map(row => ({
          branchCode:        row.branch_code,
          branchName:        row.branch_name,
          product:           row.product,
          totalApplications: parseInt(row.count, 10),
          totalAmount:       parseFloat(row.total_amount || 0),
          formattedAmount:   'INR ' + formatINR(row.total_amount || 0)
        })));
      }

      // ----- Report 3: Disbursements (date range + branch) -----
      if (pathname === '/api/reports/disbursements') {
        const fromDate  = query.get('from')   || null;
        const toDate    = query.get('to')     || null;
        const branch    = query.get('branch') || null;
        const groupBy   = query.get('groupBy') || 'daily'; // daily | monthly | quarterly
        let dateTrunc = 'day';
        if (groupBy === 'monthly')   dateTrunc = 'month';
        if (groupBy === 'quarterly') dateTrunc = 'quarter';

        let sql = `
          SELECT DATE_TRUNC('${dateTrunc}', d.disbursed_at)::DATE AS period,
                 a.branch_code, a.branch_name,
                 COUNT(*) AS count, SUM(d.amount) AS total_amount
          FROM disbursement d
          JOIN loan_application a ON a.application_id = d.application_id
          WHERE 1=1`;
        const params = [];
        if (fromDate) { params.push(fromDate); sql += ` AND d.disbursed_at >= $${params.length}::DATE`; }
        if (toDate)   { params.push(toDate);   sql += ` AND d.disbursed_at <  ($${params.length}::DATE + INTERVAL '1 day')`; }
        if (branch)   { params.push(branch);   sql += ` AND a.branch_code = $${params.length}`; }
        sql += ' GROUP BY period, a.branch_code, a.branch_name ORDER BY period DESC;';

        const r = await p.query(sql, params);
        return res.status(200).json(r.rows.map(row => {
          const pStr = row.period instanceof Date ? row.period.toISOString().split('T')[0] : String(row.period);
          return {
            period:             pStr,
            branchCode:         row.branch_code,
            branchName:         row.branch_name,
            periodOrBranch:     `${pStr} (${row.branch_code} - ${row.branch_name})`,
            totalDisbursements: parseInt(row.count, 10),
            totalAmount:        parseFloat(row.total_amount || 0),
            formattedAmount:    'INR ' + formatINR(row.total_amount || 0)
          };
        }));
      }

      // ----- Report 4: Overdue reviews (UNDER_REVIEW > 2 calendar days) -----
      if (pathname === '/api/reports/overdue') {
        const r = await p.query(`
          SELECT a.application_number, c.name AS customer_name, a.product,
                 a.requested_amount, a.submitted_at,
                 ($1::DATE - a.submitted_at::DATE) AS days_waiting
          FROM loan_application a
          LEFT JOIN customer c ON a.customer_id = c.customer_id
          WHERE a.status = 'UNDER_REVIEW'
            AND a.submitted_at IS NOT NULL
            AND ($1::DATE - a.submitted_at::DATE) > ${OVERDUE_DAYS}
          ORDER BY days_waiting DESC;
        `, [businessDate]);
        return res.status(200).json(r.rows.map(row => ({
          applicationNumber: row.application_number,
          customerName:      row.customer_name || '',
          product:           row.product || '',
          formattedAmount:   'INR ' + formatINR(row.requested_amount),
          submittedAt:       row.submitted_at instanceof Date ? row.submitted_at.toISOString().split('T')[0] : String(row.submitted_at),
          daysWaiting:       parseInt(row.days_waiting, 10)
        })));
      }

      // ----- Report 5: Customers without disbursement -----
      if (pathname === '/api/reports/no-disbursement') {
        const r = await p.query(`
          SELECT c.customer_id, c.name, c.mobile, COUNT(la.application_id) AS total_applications
          FROM customer c
          JOIN loan_application la ON la.customer_id = c.customer_id
          WHERE NOT EXISTS (
            SELECT 1 FROM loan_application la2
            JOIN disbursement d ON d.application_id = la2.application_id
            WHERE la2.customer_id = c.customer_id
          )
          GROUP BY c.customer_id, c.name, c.mobile
          ORDER BY c.name;
        `);
        return res.status(200).json(r.rows.map(row => ({
          customerId:        row.customer_id,
          customerName:      row.name,
          name:              row.name,
          mobile:            row.mobile,
          totalApplications: parseInt(row.total_applications, 10)
        })));
      }

      // ----- Report 6: Repeat applicants (GROUP BY … HAVING count > 1) -----
      if (pathname === '/api/reports/repeat-applicants') {
        const r = await p.query(`
          SELECT c.customer_id, c.name, c.mobile,
                 COUNT(a.application_id)        AS application_count,
                 SUM(a.requested_amount)        AS total_requested
          FROM customer c
          JOIN loan_application a ON a.customer_id = c.customer_id
          GROUP BY c.customer_id, c.name, c.mobile
          HAVING COUNT(a.application_id) > 1
          ORDER BY application_count DESC;
        `);
        return res.status(200).json(r.rows.map(row => ({
          customerId:        row.customer_id,
          customerName:      row.name,
          name:              row.name,
          mobile:            row.mobile,
          applicationCount:  parseInt(row.application_count, 10),
          totalRequested:    parseFloat(row.total_requested || 0),
          formattedAmount:   'INR ' + formatINR(row.total_requested || 0),
          formattedTotal:    'INR ' + formatINR(row.total_requested || 0)
        })));
      }

      return res.status(404).json({ error: 'Report not found.' });

    } catch (err) {
      return res.status(500).json({ error: 'Database error generating report: ' + err.message });
    }
  }

  // =========================================================================
  // 10. UTILITY — run overdue monitor + core banking push
  // =========================================================================
  if (pathname === '/api/utilities/run' && method === 'POST') {
    const client = await p.connect();
    try {
      await client.query('BEGIN');

      // --- Overdue monitor: flag UNDER_REVIEW apps older than OVERDUE_DAYS ---
      const overdueApps = await client.query(`
        SELECT application_id FROM loan_application
        WHERE status = 'UNDER_REVIEW'
          AND submitted_at IS NOT NULL
          AND ($1::DATE - submitted_at::DATE) > ${OVERDUE_DAYS};
      `, [businessDate]);

      let alertsCreated = 0;
      for (const row of overdueApps.rows) {
        const existsRes = await client.query(
          `SELECT 1 FROM system_alerts WHERE application_id=$1 AND alert_type='REVIEW_OVERDUE';`,
          [row.application_id]
        );
        if (existsRes.rows.length === 0) {
          await client.query(
            `INSERT INTO system_alerts (application_id, alert_type, created_at) VALUES ($1, 'REVIEW_OVERDUE', NOW());`,
            [row.application_id]
          );
          alertsCreated++;
        }
      }

      // --- Core banking push: DISBURSED → ng_core_banking → EXIT ---
      const disbursedApps = await client.query(`
        SELECT a.application_id, a.customer_id, a.requested_amount,
               c.name AS customer_name, c.address, c.date_of_birth, c.kyc_details,
               d.amount AS disbursed_amount, d.disbursed_at
        FROM loan_application a
        JOIN customer c     ON c.customer_id     = a.customer_id
        JOIN disbursement d ON d.application_id  = a.application_id
        WHERE a.status = 'DISBURSED'
          AND NOT EXISTS (
            SELECT 1 FROM ng_core_banking cb WHERE cb.application_id = a.application_id
          );
      `);

      let pushedCount = 0;
      for (const row of disbursedApps.rows) {
        await client.query(`
          INSERT INTO ng_core_banking
            (application_id, customer_id, customer_name, address, dob, kyc_details,
             approved_amount, disbursed_amount, disbursed_date, synced_at)
          VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,NOW());
        `, [
          row.application_id, row.customer_id, row.customer_name, row.address,
          row.date_of_birth instanceof Date ? row.date_of_birth.toISOString().split('T')[0] : row.date_of_birth,
          row.kyc_details, row.requested_amount, row.disbursed_amount,
          row.disbursed_at instanceof Date ? row.disbursed_at.toISOString().split('T')[0] : row.disbursed_at
        ]);

        await client.query(`UPDATE loan_application SET status='EXIT' WHERE application_id=$1;`, [row.application_id]);

        await client.query(`
          INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
          VALUES ($1,'CORE_BANKING_PUSH','DISBURSED','EXIT','UTILITY_CRON',NOW(),'Pushed to NG_CORE_BANKING; case moved to EXIT.');
        `, [row.application_id]);

        pushedCount++;
      }

      // Log utility run
      await client.query(`
        INSERT INTO utility_run (utility_name, started_at, completed_at, result, applications_processed, alerts_created)
        VALUES ('COMBINED_UTILITY', NOW(), NOW(), 'SUCCESS', $1, $2);
      `, [overdueApps.rows.length + disbursedApps.rows.length, alertsCreated]);

      await client.query('COMMIT');
      return res.status(200).json({
        status: 'SUCCESS',
        timestamp: new Date().toISOString(),
        businessDate,
        overdueMonitor:  { applicationsChecked: overdueApps.rows.length, alertsCreated },
        coreBankingPush: { loansPushedToCore: pushedCount, newStatus: 'EXIT' }
      });
    } catch (err) {
      await client.query('ROLLBACK');
      return res.status(500).json({ error: 'Utility run failed: ' + err.message });
    } finally {
      client.release();
    }
  }

  // =========================================================================
  // 11. UTILITY CSV EXPORT — pending reviews with OverdueFlag
  // =========================================================================
  if (pathname === '/api/utilities/csv' && method === 'GET') {
    try {
      const qDate = query.get('businessDate');
      const csvBusinessDate = (qDate && /^\d{4}-\d{2}-\d{2}$/.test(qDate)) ? qDate : businessDate;
      const r = await p.query(`
        SELECT a.application_number, c.name AS customer_name, a.customer_id, a.product,
               a.branch_code, a.requested_amount, a.submitted_at,
               ($1::DATE - a.submitted_at::DATE) AS days_waiting
        FROM loan_application a
        LEFT JOIN customer c ON a.customer_id = c.customer_id
        WHERE a.status = 'UNDER_REVIEW' AND a.submitted_at IS NOT NULL
        ORDER BY a.submitted_at ASC;
      `, [csvBusinessDate]);

      const dateStr = csvBusinessDate.replace(/-/g, '');
      const csvRows = ['ApplicationNumber,CustomerName,CustomerID,Product,BranchCode,RequestedAmount,SubmittedAt,DaysWaiting,OverdueFlag'];
      r.rows.forEach(a => {
        const sDate    = a.submitted_at instanceof Date ? a.submitted_at.toISOString().split('T')[0] : (a.submitted_at || '');
        const days     = parseInt(a.days_waiting, 10) || 0;
        const overdue  = days > OVERDUE_DAYS ? 'Yes' : 'No';
        csvRows.push(`${a.application_number},"${a.customer_name || a.customer_id}",${a.customer_id},${a.product},${a.branch_code || ''},${a.requested_amount},${sDate},${days},${overdue}`);
      });

      res.setHeader('Content-Type', 'text/csv');
      res.setHeader('Content-Disposition', `attachment; filename="pending_reviews_${dateStr}.csv"`);
      return res.status(200).send(csvRows.join('\n'));
    } catch (err) {
      return res.status(500).json({ error: 'Failed to generate CSV: ' + err.message });
    }
  }

  // =========================================================================
  // 12. SYSTEM ALERTS
  // =========================================================================
  if (pathname === '/api/alerts' && method === 'GET') {
    try {
      const r = await p.query(`
        SELECT s.alert_id, s.application_id, a.application_number, s.alert_type, s.created_at
        FROM system_alerts s
        JOIN loan_application a ON a.application_id = s.application_id
        ORDER BY s.created_at DESC;
      `);
      return res.status(200).json(r.rows);
    } catch (err) {
      return res.status(500).json({ error: 'DB error fetching alerts: ' + err.message });
    }
  }

  // =========================================================================
  // 13. NG_CORE_BANKING log
  // =========================================================================
  if (pathname === '/api/core-banking' && method === 'GET') {
    try {
      const r = await p.query('SELECT * FROM ng_core_banking ORDER BY synced_at DESC;');
      return res.status(200).json(r.rows);
    } catch (err) {
      return res.status(500).json({ error: 'DB error fetching core banking log: ' + err.message });
    }
  }

  // 404
  return res.status(404).json({ error: 'API endpoint not found', path: pathname });
}

// ---------------------------------------------------------------------------
// Local development server
// ---------------------------------------------------------------------------
const isMain = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename);
if (isMain) {
  const PORT = process.env.PORT || 3000;
  const server = http.createServer(async (req, res) => {
    res.status = code => { res.statusCode = code; return res; };
    res.json   = obj  => { res.setHeader('Content-Type','application/json'); res.end(JSON.stringify(obj)); return res; };
    res.send   = data => { res.end(data); return res; };

    if (req.url.startsWith('/api/')) return handler(req, res);

    const cleanUrl = req.url.split('?')[0];
    let filePath = path.join(__dirname, '..', 'public', cleanUrl === '/' ? 'index.html' : cleanUrl);
    if (!fs.existsSync(filePath)) filePath = path.join(__dirname, '..', cleanUrl === '/' ? 'index.html' : cleanUrl);

    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const exts = { '.html':'text/html', '.css':'text/css', '.js':'application/javascript', '.json':'application/json', '.png':'image/png', '.jpg':'image/jpeg', '.svg':'image/svg+xml', '.ico':'image/x-icon' };
      res.setHeader('Content-Type', exts[path.extname(filePath).toLowerCase()] || 'text/plain');
      return res.end(fs.readFileSync(filePath));
    }

    const idx = path.join(__dirname, '..', 'public', 'index.html');
    if (fs.existsSync(idx)) { res.setHeader('Content-Type','text/html'); return res.end(fs.readFileSync(idx)); }
    res.statusCode = 404; res.end('Not Found');
  });
  server.listen(PORT, () => {
    console.log(`\n  Mini LOS running at http://localhost:${PORT}`);
    console.log(`  Supabase project: ${SUPABASE_PROJECT_REF}\n`);
  });
}
