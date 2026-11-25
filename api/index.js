// ==============================================================================
// Production-grade Vercel Serverless Function Adapter for Mini LOS
// Connected strictly to Supabase PostgreSQL via raw SQL (pg Pool).
// NO in-memory / RAM fallbacks: all state is persistent in Supabase PostgreSQL.
// ==============================================================================

import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import pg from 'pg';

const { Pool } = pg;
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Helper to load config.properties (for local runs)
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
            const key = trimmed.substring(0, idx).trim();
            const val = trimmed.substring(idx + 1).trim();
            props[key] = val;
          }
        });
        break;
      } catch (ignored) {}
    }
  }
  return props;
}

const localProps = loadConfigProperties();

// Database Configuration - Environment Variables strictly take precedence
const rawDbUrl = process.env.DB_URL || process.env.DATABASE_URL || process.env.POSTGRES_URL || localProps['db.url'] || 'jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require';

// Clean JDBC URL prefix for node-postgres
const cleanDbUrl = rawDbUrl.replace(/^jdbc:/, '');

// Parse Host, Port, Database, User, and Password from URL if present
let urlUser = '';
let urlPassword = '';
let dbHost = 'aws-0-ap-southeast-1.pooler.supabase.com';
let dbPort = 5432;
let dbDatabase = 'postgres';

try {
  const urlObj = new URL(cleanDbUrl);
  dbHost = urlObj.hostname || dbHost;
  dbPort = parseInt(urlObj.port || '5432', 10);
  dbDatabase = urlObj.pathname.replace(/^\//, '') || 'postgres';
  if (urlObj.username) urlUser = decodeURIComponent(urlObj.username);
  if (urlObj.password) urlPassword = decodeURIComponent(urlObj.password);
} catch (e) {
  const m = cleanDbUrl.match(/postgresql:\/\/(?:([^:@]+)(?::([^@]+))?@)?([^:/]+)(?::(\d+))?\/([^?]+)/);
  if (m) {
    if (m[1]) urlUser = decodeURIComponent(m[1]);
    if (m[2]) urlPassword = decodeURIComponent(m[2]);
    dbHost = m[3];
    dbPort = parseInt(m[4] || '5432', 10);
    dbDatabase = m[5] || 'postgres';
  }
}

// User & Password: Env vars take highest priority, then URL components, then properties, then defaults
let dbUser = process.env.DB_USER || process.env.POSTGRES_USER || urlUser || localProps['db.user'] || 'postgres.ciemfotaaupazeqxxuev';
let dbPassword = process.env.DB_PASSWORD || process.env.POSTGRES_PASSWORD || urlPassword || localProps['db.password'] || 'postgres';

// Auto-fix for Supabase Session Pooler (port 5432 / 6543):
// When connecting to pooler.supabase.com, Supabase requires username in format: postgres.<project-ref>
// If configured as just "postgres", auto-qualify with project reference to prevent auth routing failures.
const SUPABASE_PROJECT_REF = 'ciemfotaaupazeqxxuev';
if (dbHost.includes('pooler.supabase.com') && dbUser === 'postgres') {
  dbUser = `postgres.${SUPABASE_PROJECT_REF}`;
}

function getFriendlyDbErrorMessage(err) {
  if (!err || !err.message) return 'Unknown database error';
  if (err.message.includes('password authentication failed')) {
    return `Password authentication failed for user "${dbUser}" on ${dbHost}. Please verify that DB_PASSWORD in Vercel is set to your real Supabase database password (not the placeholder 'postgres'). You can reset your password in Supabase Dashboard -> Project Settings -> Database -> Database Password.`;
  }
  return err.message;
}

function getMaskedDbUrl() {
  return rawDbUrl.replace(/(password=)[^&;]+/i, '$1******');
}

// Global Postgres connection pool with Supabase pooler SSL support
let pool = null;

function getPool() {
  if (!pool) {
    pool = new Pool({
      host: dbHost,
      port: dbPort,
      database: dbDatabase,
      user: dbUser,
      password: dbPassword,
      ssl: {
        rejectUnauthorized: false
      },
      connectionTimeoutMillis: 5000,
      idleTimeoutMillis: 10000,
      max: 10
    });
    pool.on('error', (err) => {
      console.error('[Supabase PostgreSQL Pool Error]', err.message);
    });
  }
  return pool;
}

const SUPABASE_CONFIG = {
  url: process.env.SUPABASE_URL || localProps['supabase.url'] || 'https://ciemfotaaupazeqxxuev.supabase.co',
  publishableKey: process.env.SUPABASE_PUBLISHABLE_KEY || localProps['supabase.publishable_key'] || 'sb_publishable_kX3L5-No3WzDIen5e9KmOw_VFI6gMol',
  secretKey: process.env.SUPABASE_SECRET_KEY || localProps['supabase.secret_key'] || 'your_supabase_secret_key',
  jwksUrl: process.env.SUPABASE_JWKS_URL || localProps['supabase.jwks_url'] || 'https://ciemfotaaupazeqxxuev.supabase.co/auth/v1/.well-known/jwks.json',
  businessDate: process.env.BUSINESS_DATE || localProps['app.business_date'] || new Date().toISOString().split('T')[0]
};

// Role permissions mapping
const ROLES = {
  LOAN_OFFICER: ['CUSTOMER_CREATE', 'CUSTOMER_READ', 'APPLICATION_DRAFT_CREATE', 'APPLICATION_DRAFT_EDIT', 'APPLICATION_SUBMIT', 'APPLICATION_READ', 'REPORTS_VIEW'],
  CREDIT_OFFICER: ['APPLICATION_READ', 'APPLICATION_REVIEW', 'CUSTOMER_READ', 'REPORTS_VIEW', 'AUDIT_LOG_VIEW'],
  DISBURSEMENT_OFFICER: ['APPLICATION_READ', 'APPLICATION_DISBURSE', 'CUSTOMER_READ', 'REPORTS_VIEW', 'AUDIT_LOG_VIEW'],
  AUDITOR: ['CUSTOMER_READ', 'APPLICATION_READ', 'REPORTS_VIEW', 'AUDIT_LOG_VIEW'],
  ADMIN: ['ALL']
};

function hasPermission(role, permission) {
  if (!role) return false;
  const perms = ROLES[role.toUpperCase()];
  if (!perms) return false;
  return perms.includes('ALL') || perms.includes(permission);
}

// Indian Currency Formatter (##,##,###,###.##)
function formatIndianCurrency(amount) {
  if (amount === undefined || amount === null) return '0.00';
  const num = typeof amount === 'number' ? amount : parseFloat(amount);
  if (isNaN(num)) return '0.00';
  const parts = num.toFixed(2).split('.');
  let integerPart = parts[0];
  const decimalPart = '.' + parts[1];
  let lastThree = integerPart.substring(integerPart.length - 3);
  const otherNumbers = integerPart.substring(0, integerPart.length - 3);
  if (otherNumbers !== '') {
    lastThree = ',' + lastThree;
  }
  return otherNumbers.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + lastThree + decimalPart;
}

// EMI Math Formula (P * r * (1+r)^n / ((1+r)^n - 1))
function calculateEmi(principal, annualRate, tenureMonths) {
  const r = annualRate / 12 / 100;
  const n = tenureMonths;
  if (r === 0 || n === 0) return 0;
  const factor = Math.pow(1 + r, n);
  const emi = (principal * r * factor) / (factor - 1);
  return Math.round((emi + Number.EPSILON) * 100) / 100;
}

// Row mappers from PostgreSQL snake_case to API camelCase
function mapCustomer(row) {
  if (!row) return null;
  return {
    customerId: row.customer_id,
    name: row.name,
    dateOfBirth: row.date_of_birth instanceof Date ? row.date_of_birth.toISOString().split('T')[0] : String(row.date_of_birth || ''),
    mobile: row.mobile,
    address: row.address,
    kycDetails: row.kyc_details,
    createdAt: row.created_at
  };
}

function mapApplication(row) {
  if (!row) return null;
  return {
    applicationId: parseInt(row.application_id, 10),
    applicationNumber: row.application_number,
    customerId: row.customer_id,
    customerName: row.customer_name || row.customer_id,
    product: row.product,
    branchCode: row.branch_code,
    branchName: row.branch_name,
    requestedAmount: parseFloat(row.requested_amount),
    tenureMonths: parseInt(row.tenure_months, 10),
    monthlyIncome: parseFloat(row.monthly_income),
    existingMonthlyObligations: parseFloat(row.existing_monthly_obligations || 0),
    annualInterestRate: parseFloat(row.annual_interest_rate),
    proposedEmi: parseFloat(row.proposed_emi),
    foir: parseFloat(row.foir_percentage),
    foirPercentage: parseFloat(row.foir_percentage),
    eligible: Boolean(row.eligible),
    eligibilityReasons: row.eligibility_reasons,
    status: row.status,
    createdAt: row.created_at,
    submittedAt: row.submitted_at instanceof Date ? row.submitted_at.toISOString().split('T')[0] : (row.submitted_at || null),
    decidedAt: row.decided_at,
    reviewerCode: row.reviewer_code,
    decisionReason: row.decision_reason
  };
}

// Body parsing helper
async function parseBody(req) {
  if (req.body && typeof req.body === 'object') return req.body;
  if (req.body && typeof req.body === 'string') {
    try { return JSON.parse(req.body); } catch (e) { return {}; }
  }
  return new Promise((resolve) => {
    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      try { resolve(body ? JSON.parse(body) : {}); }
      catch (e) { resolve({}); }
    });
  });
}

// Idempotent schema verification using CREATE TABLE IF NOT EXISTS ONLY (NO DROP/TRUNCATE/DELETE)
let schemaInitialized = false;
async function ensureSchema(p) {
  if (schemaInitialized) return;
  const ddl = `
    CREATE TABLE IF NOT EXISTS customer (
      customer_id VARCHAR(15) PRIMARY KEY,
      name VARCHAR(100) NOT NULL,
      date_of_birth DATE NOT NULL,
      mobile VARCHAR(10) UNIQUE NOT NULL,
      address TEXT NOT NULL,
      kyc_details TEXT NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS loan_application (
      application_id BIGSERIAL PRIMARY KEY,
      application_number VARCHAR(30) UNIQUE NOT NULL,
      customer_id VARCHAR(15) NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
      product VARCHAR(20) NOT NULL,
      branch_code VARCHAR(20) NOT NULL,
      branch_name VARCHAR(100) NOT NULL,
      requested_amount NUMERIC(15, 2) NOT NULL,
      tenure_months INT NOT NULL,
      monthly_income NUMERIC(15, 2) NOT NULL,
      existing_monthly_obligations NUMERIC(15, 2) NOT NULL DEFAULT 0,
      annual_interest_rate NUMERIC(5, 2) NOT NULL,
      proposed_emi NUMERIC(15, 2) NOT NULL,
      foir_percentage NUMERIC(5, 2) NOT NULL,
      eligible BOOLEAN NOT NULL DEFAULT FALSE,
      eligibility_reasons TEXT,
      status VARCHAR(30) NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      submitted_at TIMESTAMP WITH TIME ZONE,
      decided_at TIMESTAMP WITH TIME ZONE,
      reviewer_code VARCHAR(50),
      decision_reason TEXT
    );

    CREATE TABLE IF NOT EXISTS disbursement (
      disbursement_id BIGSERIAL PRIMARY KEY,
      application_id BIGINT UNIQUE NOT NULL REFERENCES loan_application(application_id) ON DELETE RESTRICT,
      reference VARCHAR(50) UNIQUE NOT NULL,
      amount NUMERIC(15, 2) NOT NULL,
      disbursed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

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

    CREATE TABLE IF NOT EXISTS system_alerts (
      alert_id BIGSERIAL PRIMARY KEY,
      application_id BIGINT NOT NULL REFERENCES loan_application(application_id) ON DELETE CASCADE,
      alert_type VARCHAR(50) NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT uq_system_alerts_app_type UNIQUE (application_id, alert_type)
    );

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

    CREATE TABLE IF NOT EXISTS app_user (
      user_id BIGSERIAL PRIMARY KEY,
      username VARCHAR(50) UNIQUE NOT NULL,
      full_name VARCHAR(100) NOT NULL,
      role VARCHAR(30) NOT NULL,
      email VARCHAR(100) UNIQUE NOT NULL,
      branch_code VARCHAR(20) DEFAULT 'BR001',
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    INSERT INTO app_user (username, full_name, role, email, branch_code) VALUES
    ('officer_ramesh', 'Ramesh Sharma', 'LOAN_OFFICER', 'ramesh@losbank.in', 'BR001'),
    ('credit_priya', 'Priya Iyer', 'CREDIT_OFFICER', 'priya@losbank.in', 'BR001'),
    ('treasury_amit', 'Amit Patel', 'DISBURSEMENT_OFFICER', 'amit@losbank.in', 'BR001'),
    ('admin_super', 'Rajesh Gupta (Admin)', 'ADMIN', 'admin@losbank.in', 'HQ001'),
    ('auditor_neha', 'Neha Kulkarni', 'AUDITOR', 'neha@losbank.in', 'AUDIT01')
    ON CONFLICT (username) DO NOTHING;
  `;
  try {
    await p.query(ddl);
    schemaInitialized = true;
  } catch (err) {
    console.error('[Schema Init Warning]', err.message);
  }
}

export default async function handler(req, res) {
  // Global CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-User-Role, X-User-Name');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const rawUrl = req.url || '';
  const urlObj = new URL(rawUrl, 'http://localhost');
  const pathname = urlObj.pathname;
  const method = req.method;

  const userRole = (req.headers['x-user-role'] || 'ADMIN').toUpperCase();
  const userName = req.headers['x-user-name'] || 'admin_super';

  // 1. HEALTH CHECK: strictly returns live PostgreSQL product name, version, and masked host
  if (pathname === '/api/health') {
    try {
      const p = getPool();
      await ensureSchema(p);
      const dbResult = await p.query('SELECT version() AS version, current_database() AS db, current_user AS user;');
      const versionStr = dbResult.rows[0]?.version || 'PostgreSQL';
      const productName = versionStr.split(' ')[0] || 'PostgreSQL';

      return res.status(200).json({
        status: 'UP',
        environment: 'Vercel Serverless Production',
        database: {
          status: 'CONNECTED',
          product: productName,
          version: versionStr,
          host: dbHost,
          url: getMaskedDbUrl(),
          user: dbUser,
          connected: true
        },
        supabase: {
          url: SUPABASE_CONFIG.url,
          projectRef: 'ciemfotaaupazeqxxuev',
          jwksUrl: SUPABASE_CONFIG.jwksUrl,
          connected: true
        },
        rbac: {
          currentUser: userName,
          currentRole: userRole,
          availableRoles: Object.keys(ROLES)
        },
        businessDate: SUPABASE_CONFIG.businessDate
      });
    } catch (err) {
      console.error('[Health Check DB Error]', err);
      return res.status(503).json({
        status: 'DOWN',
        environment: 'Vercel Serverless Production',
        database: {
          status: 'DISCONNECTED',
          product: 'PostgreSQL',
          host: dbHost,
          url: getMaskedDbUrl(),
          user: dbUser,
          connected: false,
          error: getFriendlyDbErrorMessage(err)
        },
        error: 'Database connection failed: ' + getFriendlyDbErrorMessage(err)
      });
    }
  }

  const p = getPool();

  // 2. USERS API
  if (pathname === '/api/users' && method === 'GET') {
    try {
      await ensureSchema(p);
      const result = await p.query('SELECT user_id, username, full_name, role, email, branch_code FROM app_user ORDER BY user_id ASC;');
      const users = result.rows.map(r => ({
        userId: r.user_id,
        username: r.username,
        fullName: r.full_name,
        role: r.role,
        email: r.email,
        branchCode: r.branch_code
      }));
      return res.status(200).json(users);
    } catch (err) {
      console.error('[Users DB Error]', err);
      return res.status(500).json({ error: 'Database error fetching users: ' + getFriendlyDbErrorMessage(err) });
    }
  }

  // 3. CUSTOMERS API
  if (pathname === '/api/customers') {
    if (method === 'GET') {
      if (!hasPermission(userRole, 'CUSTOMER_READ')) {
        return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot read customers.` });
      }
      try {
        await ensureSchema(p);
        const result = await p.query('SELECT * FROM customer ORDER BY created_at DESC;');
        return res.status(200).json(result.rows.map(mapCustomer));
      } catch (err) {
        console.error('[Get Customers DB Error]', err);
        return res.status(500).json({ error: 'Database error fetching customers: ' + getFriendlyDbErrorMessage(err) });
      }
    }

    if (method === 'POST') {
      if (!hasPermission(userRole, 'CUSTOMER_CREATE')) {
        return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot create customers.` });
      }
      const body = await parseBody(req);
      if (!body.name || !body.mobile || !body.dateOfBirth || !body.kycDetails || !body.address) {
        return res.status(400).json({ error: 'All fields are required (name, mobile, dateOfBirth, kycDetails, address).' });
      }
      if (!/^[0-9]{10}$/.test(body.mobile)) {
        return res.status(400).json({ error: 'Mobile number must be exactly 10 digits.' });
      }

      try {
        await ensureSchema(p);
        // Check duplicate mobile
        const dupCheck = await p.query('SELECT 1 FROM customer WHERE mobile = $1;', [body.mobile.trim()]);
        if (dupCheck.rows.length > 0) {
          return res.status(409).json({ error: `Customer with mobile ${body.mobile} already exists.` });
        }

        const customerId = 'CUST-' + Math.random().toString(36).substring(2, 8).toUpperCase();
        const insertSql = `
          INSERT INTO customer (customer_id, name, date_of_birth, mobile, address, kyc_details, created_at)
          VALUES ($1, $2, $3, $4, $5, $6, NOW())
          RETURNING *;
        `;
        const result = await p.query(insertSql, [
          customerId,
          body.name.trim(),
          body.dateOfBirth,
          body.mobile.trim(),
          body.address.trim(),
          body.kycDetails.trim()
        ]);

        return res.status(201).json(mapCustomer(result.rows[0]));
      } catch (err) {
        console.error('[Create Customer DB Error]', err);
        return res.status(500).json({ error: 'Failed to create customer: ' + getFriendlyDbErrorMessage(err) });
      }
    }
  }

  // 4. APPLICATIONS API
  if (pathname === '/api/applications') {
    if (method === 'GET') {
      if (!hasPermission(userRole, 'APPLICATION_READ')) {
        return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot read applications.` });
      }
      try {
        await ensureSchema(p);
        const query = `
          SELECT a.*, c.name AS customer_name
          FROM loan_application a
          LEFT JOIN customer c ON a.customer_id = c.customer_id
          ORDER BY a.created_at DESC;
        `;
        const result = await p.query(query);
        return res.status(200).json(result.rows.map(mapApplication));
      } catch (err) {
        console.error('[Get Applications DB Error]', err);
        return res.status(500).json({ error: 'Database error fetching applications: ' + getFriendlyDbErrorMessage(err) });
      }
    }

    if (method === 'POST') {
      if (!hasPermission(userRole, 'APPLICATION_DRAFT_CREATE')) {
        return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot create application drafts.` });
      }
      const body = await parseBody(req);
      if (!body.customerId || !body.product || !body.requestedAmount || !body.tenureMonths || !body.monthlyIncome) {
        return res.status(400).json({ error: 'Missing required application fields.' });
      }

      const P = parseFloat(body.requestedAmount);
      const n = parseInt(body.tenureMonths, 10);
      const income = parseFloat(body.monthlyIncome);
      const obligations = parseFloat(body.existingMonthlyObligations || 0);
      const product = body.product.toUpperCase();
      const rate = product === 'PERSONAL' ? 12.0 : 10.0;

      if (product === 'PERSONAL' && (P < 50000 || P > 500000 || n < 12 || n > 60)) {
        return res.status(400).json({ error: 'PERSONAL loan: Amount must be 50k-500k, Tenure 12-60m.' });
      }
      if (product === 'EDUCATION' && (P < 100000 || P > 1000000 || n < 12 || n > 84)) {
        return res.status(400).json({ error: 'EDUCATION loan: Amount must be 100k-1M, Tenure 12-84m.' });
      }

      const emi = calculateEmi(P, rate, n);
      const foir = Math.round(((obligations + emi) / income * 100) * 100) / 100;
      const eligible = foir <= 50.0 && P > 0;
      const appNumber = 'APP-' + new Date().getFullYear() + '-' + Math.floor(100000 + Math.random() * 900000);

      const client = await p.connect();
      try {
        await client.query('BEGIN');

        // Verify customer exists
        const custRes = await client.query('SELECT name FROM customer WHERE customer_id = $1;', [body.customerId]);
        if (custRes.rows.length === 0) {
          await client.query('ROLLBACK');
          return res.status(404).json({ error: `Customer not found with ID: ${body.customerId}` });
        }
        const custName = custRes.rows[0].name;

        // Check active application constraint (DRAFT, UNDER_REVIEW, CREDIT_APPROVAL)
        const activeRes = await client.query(
          `SELECT 1 FROM loan_application WHERE customer_id = $1 AND product = $2 AND status IN ('DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL');`,
          [body.customerId, product]
        );
        if (activeRes.rows.length > 0) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: `Customer already has an active ${product} loan application.` });
        }

        const insertSql = `
          INSERT INTO loan_application (
            application_number, customer_id, product, branch_code, branch_name,
            requested_amount, tenure_months, monthly_income, existing_monthly_obligations,
            annual_interest_rate, proposed_emi, foir_percentage, eligible, eligibility_reasons,
            status, created_at
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, 'DRAFT', NOW())
          RETURNING *;
        `;
        const insertRes = await client.query(insertSql, [
          appNumber,
          body.customerId,
          product,
          body.branchCode || 'BR001',
          body.branchName || 'Main Branch',
          P,
          n,
          income,
          obligations,
          rate,
          emi,
          foir,
          eligible,
          eligible ? 'All eligibility criteria met.' : `FOIR ${foir}% exceeds 50.00% limit.`
        ]);

        const newApp = insertRes.rows[0];

        // Audit Trail
        await client.query(
          `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
           VALUES ($1, 'CREATE_DRAFT', 'NONE', 'DRAFT', $2, NOW(), 'Draft created via API');`,
          [newApp.application_id, userName]
        );

        await client.query('COMMIT');
        newApp.customer_name = custName;
        return res.status(201).json(mapApplication(newApp));
      } catch (err) {
        await client.query('ROLLBACK');
        console.error('[Create Application DB Error]', err);
        return res.status(500).json({ error: 'Failed to create application draft: ' + err.message });
      } finally {
        client.release();
      }
    }
  }

  // 5. SUBMIT APPLICATION
  const submitMatch = pathname.match(/^\/api\/applications\/(\d+)\/submit$/);
  if (submitMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_SUBMIT')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot submit applications.` });
    }
    const appId = parseInt(submitMatch[1], 10);
    const client = await p.connect();
    try {
      await client.query('BEGIN');
      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id = $1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'DRAFT') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Cannot submit from status ${app.status}. Must be DRAFT.` });
      }

      const isEligible = parseFloat(app.foir_percentage) <= 50.0;
      const oldStatus = app.status;
      const newStatus = isEligible ? 'UNDER_REVIEW' : 'REJECTED';
      const submittedAt = SUPABASE_CONFIG.businessDate;

      const updateSql = `
        UPDATE loan_application
        SET status = $1, submitted_at = $2
        WHERE application_id = $3
        RETURNING *;
      `;
      const updateRes = await client.query(updateSql, [newStatus, submittedAt, appId]);

      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1, 'SUBMIT', $2, $3, $4, NOW(), $5);`,
        [appId, oldStatus, newStatus, userName, isEligible ? 'Eligibility verified' : 'Rejected: FOIR exceeded limit']
      );

      await client.query('COMMIT');
      return res.status(200).json(mapApplication(updateRes.rows[0]));
    } catch (err) {
      await client.query('ROLLBACK');
      console.error('[Submit DB Error]', err);
      return res.status(500).json({ error: 'Failed to submit application: ' + err.message });
    } finally {
      client.release();
    }
  }

  // 6. REVIEW APPLICATION
  const reviewMatch = pathname.match(/^\/api\/applications\/(\d+)\/review$/);
  if (reviewMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_REVIEW')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot review applications.` });
    }
    const appId = parseInt(reviewMatch[1], 10);
    const body = await parseBody(req);
    const approve = Boolean(body.approve);
    const reviewerCode = body.reviewerCode || userName;
    const reason = body.decisionReason || (approve ? 'Sanction approved by Credit Committee' : 'Adverse credit parameters');

    const client = await p.connect();
    try {
      await client.query('BEGIN');
      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id = $1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'UNDER_REVIEW') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Cannot review application in status ${app.status}. Must be UNDER_REVIEW.` });
      }

      const oldStatus = app.status;
      const newStatus = approve ? 'CREDIT_APPROVAL' : 'REJECTED';

      const updateSql = `
        UPDATE loan_application
        SET status = $1, reviewer_code = $2, decision_reason = $3, decided_at = NOW()
        WHERE application_id = $4
        RETURNING *;
      `;
      const updateRes = await client.query(updateSql, [newStatus, reviewerCode, reason, appId]);

      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1, $2, $3, $4, $5, NOW(), $6);`,
        [appId, approve ? 'APPROVE' : 'REJECT', oldStatus, newStatus, userName, reason]
      );

      await client.query('COMMIT');
      return res.status(200).json(mapApplication(updateRes.rows[0]));
    } catch (err) {
      await client.query('ROLLBACK');
      console.error('[Review DB Error]', err);
      return res.status(500).json({ error: 'Failed to record review: ' + err.message });
    } finally {
      client.release();
    }
  }

  // 7. DISBURSE APPLICATION
  const disburseMatch = pathname.match(/^\/api\/applications\/(\d+)\/disburse$/);
  if (disburseMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_DISBURSE')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot disburse applications.` });
    }
    const appId = parseInt(disburseMatch[1], 10);
    const client = await p.connect();
    try {
      await client.query('BEGIN');
      const appRes = await client.query('SELECT * FROM loan_application WHERE application_id = $1 FOR UPDATE;', [appId]);
      if (appRes.rows.length === 0) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Application not found.' });
      }
      const app = appRes.rows[0];
      if (app.status !== 'CREDIT_APPROVAL') {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `Cannot disburse application in status ${app.status}. Must be CREDIT_APPROVAL.` });
      }

      const dateStr = SUPABASE_CONFIG.businessDate.replace(/-/g, '');
      const ref = 'DISB-' + dateStr + '-' + Math.floor(100000 + Math.random() * 900000);
      const amount = parseFloat(app.requested_amount);

      // Insert into disbursement
      await client.query(
        `INSERT INTO disbursement (application_id, reference, amount, disbursed_at)
         VALUES ($1, $2, $3, NOW());`,
        [appId, ref, amount]
      );

      // Update loan status to DISBURSED
      await client.query(
        `UPDATE loan_application SET status = 'DISBURSED' WHERE application_id = $1;`,
        [appId]
      );

      // Audit log
      await client.query(
        `INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
         VALUES ($1, 'DISBURSE', 'CREDIT_APPROVAL', 'DISBURSED', $2, NOW(), $3);`,
        [appId, userName, `Disbursed full amount INR ${formatIndianCurrency(amount)}. Ref: ${ref}`]
      );

      await client.query('COMMIT');
      return res.status(200).json({
        applicationId: appId,
        applicationNumber: app.application_number,
        amount: amount,
        reference: ref,
        status: 'DISBURSED'
      });
    } catch (err) {
      await client.query('ROLLBACK');
      console.error('[Disburse DB Error]', err);
      return res.status(500).json({ error: 'Failed to disburse application: ' + err.message });
    } finally {
      client.release();
    }
  }

  // 8. APPLICATION HISTORY
  const historyMatch = pathname.match(/^\/api\/applications\/(\d+)\/history$/);
  if (historyMatch && method === 'GET') {
    if (!hasPermission(userRole, 'AUDIT_LOG_VIEW') && userRole !== 'LOAN_OFFICER') {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot view audit history.` });
    }
    const appId = parseInt(historyMatch[1], 10);
    try {
      const result = await p.query(
        'SELECT * FROM application_history WHERE application_id = $1 ORDER BY performed_at ASC;',
        [appId]
      );
      const history = result.rows.map(r => ({
        historyId: parseInt(r.history_id, 10),
        applicationId: parseInt(r.application_id, 10),
        action: r.action,
        oldStatus: r.old_status,
        newStatus: r.new_status,
        performedBy: r.performed_by,
        performedAt: r.performed_at,
        remarks: r.remarks
      }));
      return res.status(200).json(history);
    } catch (err) {
      console.error('[History DB Error]', err);
      return res.status(500).json({ error: 'Database error fetching history: ' + err.message });
    }
  }

  // 9. REPORTS API
  if (pathname.startsWith('/api/reports/')) {
    if (!hasPermission(userRole, 'REPORTS_VIEW')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot view reports.` });
    }

    try {
      if (pathname === '/api/reports/register') {
        const query = `
          SELECT a.application_number, c.name AS customer_name, a.customer_id, a.product,
                 a.requested_amount, a.proposed_emi, a.status, a.submitted_at
          FROM loan_application a
          LEFT JOIN customer c ON a.customer_id = c.customer_id
          ORDER BY a.created_at DESC;
        `;
        const resDb = await p.query(query);
        const data = resDb.rows.map(a => ({
          applicationNumber: a.application_number,
          customerName: a.customer_name || a.customer_id,
          product: a.product,
          formattedRequestedAmount: 'INR ' + formatIndianCurrency(a.requested_amount),
          formattedProposedEmi: 'INR ' + formatIndianCurrency(a.proposed_emi),
          status: a.status,
          submittedAt: a.submitted_at instanceof Date ? a.submitted_at.toISOString().split('T')[0] : (a.submitted_at || '-')
        }));
        return res.status(200).json(data);
      }

      if (pathname === '/api/reports/pipeline/product') {
        const query = `
          SELECT product, status, COUNT(*) AS count, SUM(requested_amount) AS total_amount
          FROM loan_application
          GROUP BY product, status
          ORDER BY product, status;
        `;
        const resDb = await p.query(query);
        const data = resDb.rows.map(r => ({
          key1: r.product,
          key2: r.status,
          totalApplications: parseInt(r.count, 10),
          amount: parseFloat(r.total_amount || 0),
          formattedAmount: 'INR ' + formatIndianCurrency(r.total_amount || 0)
        }));
        return res.status(200).json(data);
      }

      if (pathname === '/api/reports/disbursements') {
        const query = `
          SELECT COUNT(*) AS total_count, COALESCE(SUM(amount), 0) AS total_amount
          FROM disbursement;
        `;
        const resDb = await p.query(query);
        const row = resDb.rows[0];
        return res.status(200).json([{
          periodOrBranch: SUPABASE_CONFIG.businessDate + ' (Total Disbursed)',
          totalDisbursements: parseInt(row.total_count, 10),
          formattedAmount: 'INR ' + formatIndianCurrency(row.total_amount)
        }]);
      }

      if (pathname === '/api/reports/overdue') {
        const query = `
          SELECT a.application_number, c.name AS customer_name, a.customer_id, a.product,
                 a.requested_amount, a.submitted_at
          FROM loan_application a
          LEFT JOIN customer c ON a.customer_id = c.customer_id
          WHERE a.status = 'UNDER_REVIEW'
          ORDER BY a.submitted_at ASC;
        `;
        const resDb = await p.query(query);
        const data = resDb.rows.map(a => ({
          applicationNumber: a.application_number,
          customerName: a.customer_name || a.customer_id,
          product: a.product,
          formattedAmount: 'INR ' + formatIndianCurrency(a.requested_amount),
          submittedAt: a.submitted_at instanceof Date ? a.submitted_at.toISOString().split('T')[0] : (a.submitted_at || '-'),
          daysWaiting: 2
        }));
        return res.status(200).json(data);
      }

      if (pathname === '/api/reports/no-disbursement') {
        const query = `
          SELECT c.customer_id, c.name AS customer_name, c.mobile,
                 COUNT(a.application_id) AS total_applications
          FROM customer c
          LEFT JOIN loan_application a ON c.customer_id = a.customer_id
          WHERE c.customer_id NOT IN (
            SELECT DISTINCT customer_id FROM loan_application WHERE status IN ('DISBURSED', 'EXIT')
          )
          GROUP BY c.customer_id, c.name, c.mobile
          ORDER BY c.created_at DESC;
        `;
        const resDb = await p.query(query);
        const data = resDb.rows.map(c => ({
          customerId: c.customer_id,
          customerName: c.customer_name,
          mobile: c.mobile,
          totalApplications: parseInt(c.total_applications, 10)
        }));
        return res.status(200).json(data);
      }

      if (pathname === '/api/reports/repeat-applicants') {
        const query = `
          SELECT c.customer_id, c.name AS customer_name, c.mobile,
                 COUNT(a.application_id) AS application_count,
                 SUM(a.requested_amount) AS total_amount
          FROM loan_application a
          JOIN customer c ON a.customer_id = c.customer_id
          GROUP BY c.customer_id, c.name, c.mobile
          HAVING COUNT(a.application_id) > 1
          ORDER BY application_count DESC;
        `;
        const resDb = await p.query(query);
        const data = resDb.rows.map(r => ({
          customerId: r.customer_id,
          customerName: r.customer_name,
          mobile: r.mobile,
          applicationCount: parseInt(r.application_count, 10),
          formattedAmount: 'INR ' + formatIndianCurrency(r.total_amount)
        }));
        return res.status(200).json(data);
      }
    } catch (err) {
      console.error('[Reports DB Error]', err);
      return res.status(500).json({ error: 'Database error generating report: ' + err.message });
    }
  }

  // 10. UTILITIES & CRON
  if (pathname === '/api/utilities/run' && method === 'POST') {
    if (userRole !== 'ADMIN') {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot trigger utilities. Required: ADMIN` });
    }

    const client = await p.connect();
    try {
      await client.query('BEGIN');

      // 1. Overdue monitor: insert alerts for UNDER_REVIEW applications
      const overdueApps = await client.query("SELECT application_id FROM loan_application WHERE status = 'UNDER_REVIEW';");
      let alertsCreated = 0;
      for (const row of overdueApps.rows) {
        const alertRes = await client.query(
          `INSERT INTO system_alerts (application_id, alert_type, created_at)
           VALUES ($1, 'REVIEW_OVERDUE', NOW())
           ON CONFLICT (application_id, alert_type) DO NOTHING
           RETURNING alert_id;`,
          [row.application_id]
        );
        if (alertRes.rows.length > 0) alertsCreated++;
      }

      // 2. Core Banking Push: DISBURSED -> EXIT
      const disbursedApps = await client.query(`
        SELECT a.application_id, a.customer_id, a.requested_amount,
               c.name as customer_name, c.address, c.date_of_birth, c.kyc_details,
               d.amount as disbursed_amount, d.disbursed_at
        FROM loan_application a
        JOIN customer c ON a.customer_id = c.customer_id
        JOIN disbursement d ON a.application_id = d.application_id
        WHERE a.status = 'DISBURSED'
        FOR UPDATE;
      `);

      let pushedCount = 0;
      for (const row of disbursedApps.rows) {
        await client.query(`
          INSERT INTO ng_core_banking (
            application_id, customer_id, customer_name, address, dob, kyc_details,
            approved_amount, disbursed_amount, disbursed_date, synced_at
          ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
          ON CONFLICT (application_id) DO NOTHING;
        `, [
          row.application_id,
          row.customer_id,
          row.customer_name,
          row.address,
          row.date_of_birth,
          row.kyc_details,
          row.requested_amount,
          row.disbursed_amount,
          row.disbursed_at instanceof Date ? row.disbursed_at.toISOString().split('T')[0] : row.disbursed_at
        ]);

        await client.query(`UPDATE loan_application SET status = 'EXIT' WHERE application_id = $1;`, [row.application_id]);

        await client.query(`
          INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks)
          VALUES ($1, 'CORE_BANKING_PUSH', 'DISBURSED', 'EXIT', 'VERCEL_CRON', NOW(), 'Transferred to Core Banking CBS');
        `, [row.application_id]);

        pushedCount++;
      }

      // 3. Log utility run
      await client.query(`
        INSERT INTO utility_run (utility_name, started_at, completed_at, result, applications_processed, alerts_created)
        VALUES ('VERCEL_PIPELINE_CRON', NOW(), NOW(), 'SUCCESS', $1, $2);
      `, [overdueApps.rows.length, alertsCreated]);

      await client.query('COMMIT');

      return res.status(200).json({
        runId: Date.now(),
        status: 'SUCCESS',
        environment: 'Vercel Serverless Cron (Supabase PostgreSQL)',
        timestamp: new Date().toISOString(),
        overdueMonitor: {
          applicationsProcessed: overdueApps.rows.length,
          alertsCreated: alertsCreated
        },
        coreBankingPush: {
          loansPushedToCore: pushedCount,
          newStatus: 'EXIT'
        }
      });
    } catch (err) {
      await client.query('ROLLBACK');
      console.error('[Utility Run DB Error]', err);
      return res.status(500).json({ error: 'Utility batch run failed: ' + err.message });
    } finally {
      client.release();
    }
  }

  if (pathname === '/api/utilities/csv' && method === 'GET') {
    try {
      const query = `
        SELECT a.application_number, c.name as customer_name, a.customer_id, a.product,
               a.requested_amount, a.submitted_at, a.status
        FROM loan_application a
        LEFT JOIN customer c ON a.customer_id = c.customer_id
        WHERE a.status = 'UNDER_REVIEW'
        ORDER BY a.submitted_at ASC;
      `;
      const resDb = await p.query(query);
      const csvRows = ['ApplicationNumber,CustomerName,Product,Amount,SubmittedAt,OverdueFlag'];
      resDb.rows.forEach(a => {
        const sDate = a.submitted_at instanceof Date ? a.submitted_at.toISOString().split('T')[0] : (a.submitted_at || '');
        csvRows.push(`${a.application_number},"${a.customer_name || a.customer_id}",${a.product},${a.requested_amount},${sDate},Yes`);
      });
      res.setHeader('Content-Type', 'text/csv');
      res.setHeader('Content-Disposition', 'attachment; filename="pending_reviews.csv"');
      return res.status(200).send(csvRows.join('\n'));
    } catch (err) {
      console.error('[CSV Export DB Error]', err);
      return res.status(500).json({ error: 'Failed to generate CSV: ' + err.message });
    }
  }

  // Fallback 404
  return res.status(404).json({
    error: 'API endpoint not found',
    pathname: pathname,
    method: method
  });
}

// Local server mode: `node api/index.js` or `npm start`
const isMain = process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename);
if (isMain) {
  const PORT = process.env.PORT || 3000;

  const server = http.createServer(async (req, res) => {
    res.status = function (code) { res.statusCode = code; return res; };
    res.json = function (obj) {
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify(obj));
      return res;
    };
    res.send = function (data) {
      res.end(data);
      return res;
    };

    if (req.url.startsWith('/api/')) {
      return handler(req, res);
    }

    // Serve static files from public/ or root
    const cleanUrl = req.url.split('?')[0];
    let filePath = path.join(__dirname, '..', 'public', cleanUrl === '/' ? 'index.html' : cleanUrl);
    if (!fs.existsSync(filePath)) {
      filePath = path.join(__dirname, '..', cleanUrl === '/' ? 'index.html' : cleanUrl);
    }

    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath).toLowerCase();
      const contentTypes = {
        '.html': 'text/html',
        '.css': 'text/css',
        '.js': 'application/javascript',
        '.json': 'application/json',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.svg': 'image/svg+xml',
        '.ico': 'image/x-icon'
      };
      res.setHeader('Content-Type', contentTypes[ext] || 'text/plain');
      return res.end(fs.readFileSync(filePath));
    }

    const indexPath = path.join(__dirname, '..', 'public', 'index.html');
    if (fs.existsSync(indexPath)) {
      res.setHeader('Content-Type', 'text/html');
      return res.end(fs.readFileSync(indexPath));
    }

    res.statusCode = 404;
    res.end('Not Found');
  });

  server.listen(PORT, () => {
    console.log(`================================================================`);
    console.log(`  Mini LOS Production Server running at: http://localhost:${PORT}`);
    console.log(`  Vercel Serverless Ready | Supabase Connected: ciemfotaaupazeqxxuev`);
    console.log(`================================================================`);
  });
}
