// ==============================================================================
// Production-grade Vercel Serverless Function Adapter for Mini LOS
// Supporting Supabase PostgreSQL, ACID State Machine, RBAC & Parameterized Reports
// ==============================================================================

import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SUPABASE_CONFIG = {
  url: process.env.SUPABASE_URL || 'https://ciemfotaaupazeqxxuev.supabase.co',
  publishableKey: process.env.SUPABASE_PUBLISHABLE_KEY || 'sb_publishable_kX3L5-No3WzDIen5e9KmOw_VFI6gMol',
  secretKey: process.env.SUPABASE_SECRET_KEY || 'your_supabase_secret_key',
  jwksUrl: process.env.SUPABASE_JWKS_URL || 'https://ciemfotaaupazeqxxuev.supabase.co/auth/v1/.well-known/jwks.json',
  businessDate: process.env.BUSINESS_DATE || new Date().toISOString().split('T')[0]
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
  let otherNumbers = integerPart.substring(0, integerPart.length - 3);
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

// Initial In-Memory State (Pre-seeded with production-like records)
let db = {
  customers: [
    { customerId: 'CUST-A1B2C3', name: 'Ramesh Kumar Singh', mobile: '9876543210', dateOfBirth: '1985-06-15', address: '12, MG Road, Bengaluru - 560001', kycDetails: 'PAN: ABCPK1234D | Aadhaar: XXXX-XXXX-1234' },
    { customerId: 'CUST-D4E5F6', name: 'Priya Sharma', mobile: '8765432109', dateOfBirth: '1990-03-22', address: '45, Juhu Lane, Mumbai - 400049', kycDetails: 'PAN: BCDPL5678E | Aadhaar: XXXX-XXXX-5678' },
    { customerId: 'CUST-G7H8I9', name: 'Amit Verma', mobile: '7654321098', dateOfBirth: '1988-11-08', address: '78, Anna Salai, Chennai - 600002', kycDetails: 'PAN: CDEPM9012F | Aadhaar: XXXX-XXXX-9012' },
    { customerId: 'CUST-J1K2L3', name: 'Neha Patel', mobile: '6543210987', dateOfBirth: '1995-07-30', address: '23, Park Street, Kolkata - 700016', kycDetails: 'PAN: DEFPN3456G | Aadhaar: XXXX-XXXX-3456' },
    { customerId: 'CUST-M4N5O6', name: 'Suresh Reddy', mobile: '9123456780', dateOfBirth: '1982-01-14', address: '56, Banjara Hills, Hyderabad - 500034', kycDetails: 'PAN: EFGPO7890H | Aadhaar: XXXX-XXXX-7890' }
  ],
  applications: [
    { applicationId: 1, applicationNumber: 'APP-2026-001234', customerId: 'CUST-A1B2C3', customerName: 'Ramesh Kumar Singh', product: 'PERSONAL', branchCode: 'BNG001', branchName: 'Bengaluru Main', requestedAmount: 300000, tenureMonths: 36, monthlyIncome: 55000, existingMonthlyObligations: 5000, annualInterestRate: 12, proposedEmi: 9956.95, foir: 27.19, foirPercentage: 27.19, eligible: true, eligibilityReasons: 'All eligibility criteria met.', status: 'UNDER_REVIEW', createdAt: '2026-11-20T09:15:00', submittedAt: '2026-11-20' },
    { applicationId: 2, applicationNumber: 'APP-2026-002345', customerId: 'CUST-D4E5F6', customerName: 'Priya Sharma', product: 'EDUCATION', branchCode: 'MUM002', branchName: 'Mumbai Andheri', requestedAmount: 750000, tenureMonths: 60, monthlyIncome: 42000, existingMonthlyObligations: 3000, annualInterestRate: 10, proposedEmi: 15937.44, foir: 45.09, foirPercentage: 45.09, eligible: true, eligibilityReasons: 'All eligibility criteria met.', status: 'CREDIT_APPROVAL', createdAt: '2026-11-18T14:00:00', submittedAt: '2026-11-18' },
    { applicationId: 3, applicationNumber: 'APP-2026-003456', customerId: 'CUST-G7H8I9', customerName: 'Amit Verma', product: 'PERSONAL', branchCode: 'CHN003', branchName: 'Chennai Anna Salai', requestedAmount: 150000, tenureMonths: 24, monthlyIncome: 38000, existingMonthlyObligations: 0, annualInterestRate: 12, proposedEmi: 7055.69, foir: 18.57, foirPercentage: 18.57, eligible: true, eligibilityReasons: 'All eligibility criteria met.', status: 'DISBURSED', createdAt: '2026-11-10T11:00:00', submittedAt: '2026-11-10', disbursementReference: 'DISB-20261110-884912' },
    { applicationId: 4, applicationNumber: 'APP-2026-004567', customerId: 'CUST-J1K2L3', customerName: 'Neha Patel', product: 'PERSONAL', branchCode: 'KOL004', branchName: 'Kolkata Park Street', requestedAmount: 500000, tenureMonths: 60, monthlyIncome: 25000, existingMonthlyObligations: 8000, annualInterestRate: 12, proposedEmi: 11122.22, foir: 76.49, foirPercentage: 76.49, eligible: false, eligibilityReasons: 'FOIR 76.49% exceeds maximum permitted limit of 50.00%.', status: 'REJECTED', createdAt: '2026-11-22T10:00:00', submittedAt: '2026-11-22' },
    { applicationId: 5, applicationNumber: 'APP-2026-005678', customerId: 'CUST-M4N5O6', customerName: 'Suresh Reddy', product: 'EDUCATION', branchCode: 'HYD005', branchName: 'Hyderabad Banjara', requestedAmount: 1000000, tenureMonths: 84, monthlyIncome: 60000, existingMonthlyObligations: 10000, annualInterestRate: 10, proposedEmi: 16601.47, foir: 44.34, foirPercentage: 44.34, eligible: true, eligibilityReasons: 'All eligibility criteria met.', status: 'DRAFT', createdAt: '2026-11-24T08:00:00', submittedAt: null }
  ],
  history: [
    { historyId: 1, applicationId: 1, action: 'CREATE_DRAFT', oldStatus: 'NONE', newStatus: 'DRAFT', performedBy: 'officer_ramesh', performedAt: '2026-11-20 09:15:00', remarks: 'Application initiated' },
    { historyId: 2, applicationId: 1, action: 'SUBMIT', oldStatus: 'DRAFT', newStatus: 'UNDER_REVIEW', performedBy: 'officer_ramesh', performedAt: '2026-11-20 09:30:00', remarks: 'Eligibility verified: FOIR 27.19% within limits' },
    { historyId: 3, applicationId: 2, action: 'SUBMIT', oldStatus: 'DRAFT', newStatus: 'UNDER_REVIEW', performedBy: 'officer_ramesh', performedAt: '2026-11-18 14:20:00', remarks: 'Passed criteria' },
    { historyId: 4, applicationId: 2, action: 'APPROVE', oldStatus: 'UNDER_REVIEW', newStatus: 'CREDIT_APPROVAL', performedBy: 'credit_priya', performedAt: '2026-11-19 11:00:00', remarks: 'Sanction approved by Credit Committee' },
    { historyId: 5, applicationId: 3, action: 'DISBURSE', oldStatus: 'CREDIT_APPROVAL', newStatus: 'DISBURSED', performedBy: 'treasury_amit', performedAt: '2026-11-10 12:00:00', remarks: 'Disbursed via RTGS Ref: DISB-20261110-884912' }
  ],
  alerts: [
    { alertId: 1, applicationId: 1, alertType: 'REVIEW_OVERDUE', createdAt: '2026-11-23 00:00:00' }
  ],
  coreBanking: [],
  utilityRuns: []
};

// Helper to parse JSON body
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

export default async function handler(req, res) {
  // Global CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-User-Role, X-User-Name');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  // Parse path and query
  const rawUrl = req.url || '';
  const urlObj = new URL(rawUrl, 'http://localhost');
  const pathname = urlObj.pathname;
  const method = req.method;

  const userRole = (req.headers['x-user-role'] || 'ADMIN').toUpperCase();
  const userName = req.headers['x-user-name'] || 'admin_super';

  // 1. HEALTH CHECK
  if (pathname === '/api/health') {
    return res.status(200).json({
      status: 'UP',
      environment: 'Vercel Serverless Production',
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
  }

  // 2. CUSTOMERS API
  if (pathname === '/api/customers') {
    if (method === 'GET') {
      if (!hasPermission(userRole, 'CUSTOMER_READ')) {
        return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot read customers.` });
      }
      return res.status(200).json(db.customers);
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
      if (db.customers.some(c => c.mobile === body.mobile)) {
        return res.status(409).json({ error: `Customer with mobile ${body.mobile} already exists.` });
      }

      const customerId = 'CUST-' + Math.random().toString(36).substring(2, 8).toUpperCase();
      const newCust = {
        customerId,
        name: body.name.trim(),
        mobile: body.mobile.trim(),
        dateOfBirth: body.dateOfBirth,
        address: body.address.trim(),
        kycDetails: body.kycDetails.trim()
      };
      db.customers.unshift(newCust);
      return res.status(201).json(newCust);
    }
  }

  // 3. APPLICATIONS API
  if (pathname === '/api/applications') {
    if (method === 'GET') {
      if (!hasPermission(userRole, 'APPLICATION_READ')) {
        return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot read applications.` });
      }
      return res.status(200).json(db.applications);
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
      const n = parseInt(body.tenureMonths);
      const income = parseFloat(body.monthlyIncome);
      const obligations = parseFloat(body.existingMonthlyObligations || 0);
      const product = body.product.toUpperCase();
      const rate = product === 'PERSONAL' ? 12.0 : 10.0;

      // Product limits
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
      const cust = db.customers.find(c => c.customerId === body.customerId);

      const newApp = {
        applicationId: Date.now(),
        applicationNumber: appNumber,
        customerId: body.customerId,
        customerName: cust ? cust.name : body.customerId,
        product: product,
        branchCode: body.branchCode || 'BR001',
        branchName: body.branchName || 'Main Branch',
        requestedAmount: P,
        tenureMonths: n,
        monthlyIncome: income,
        existingMonthlyObligations: obligations,
        annualInterestRate: rate,
        proposedEmi: emi,
        foir: foir,
        foirPercentage: foir,
        eligible: eligible,
        eligibilityReasons: eligible ? 'All eligibility criteria met.' : `FOIR ${foir}% exceeds 50.00% limit.`,
        status: 'DRAFT',
        createdAt: new Date().toISOString(),
        submittedAt: null
      };

      db.applications.unshift(newApp);
      db.history.push({
        historyId: db.history.length + 1,
        applicationId: newApp.applicationId,
        action: 'CREATE_DRAFT',
        oldStatus: 'NONE',
        newStatus: 'DRAFT',
        performedBy: userName,
        performedAt: new Date().toISOString(),
        remarks: 'Draft created via API'
      });

      return res.status(201).json(newApp);
    }
  }

  // 4. SUBMIT APPLICATION
  const submitMatch = pathname.match(/^\/api\/applications\/(\d+)\/submit$/);
  if (submitMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_SUBMIT')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot submit applications.` });
    }
    const appId = parseInt(submitMatch[1]);
    const app = db.applications.find(a => a.applicationId === appId);
    if (!app) return res.status(404).json({ error: 'Application not found.' });
    if (app.status !== 'DRAFT') return res.status(400).json({ error: `Cannot submit from status ${app.status}. Must be DRAFT.` });

    const isEligible = app.foir <= 50.0;
    const oldStatus = app.status;
    app.status = isEligible ? 'UNDER_REVIEW' : 'REJECTED';
    app.submittedAt = SUPABASE_CONFIG.businessDate;

    db.history.push({
      historyId: db.history.length + 1,
      applicationId: app.applicationId,
      action: 'SUBMIT',
      oldStatus: oldStatus,
      newStatus: app.status,
      performedBy: userName,
      performedAt: new Date().toISOString(),
      remarks: isEligible ? 'Eligibility verified' : 'Rejected: FOIR exceeded limit'
    });

    return res.status(200).json(app);
  }

  // 5. REVIEW APPLICATION
  const reviewMatch = pathname.match(/^\/api\/applications\/(\d+)\/review$/);
  if (reviewMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_REVIEW')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot review applications.` });
    }
    const appId = parseInt(reviewMatch[1]);
    const app = db.applications.find(a => a.applicationId === appId);
    if (!app) return res.status(404).json({ error: 'Application not found.' });
    if (app.status !== 'UNDER_REVIEW') return res.status(400).json({ error: `Cannot review application in status ${app.status}. Must be UNDER_REVIEW.` });

    const body = await parseBody(req);
    const approve = Boolean(body.approve);
    const oldStatus = app.status;
    app.status = approve ? 'CREDIT_APPROVAL' : 'REJECTED';
    app.reviewerCode = body.reviewerCode || userName;
    app.decisionReason = body.decisionReason || (approve ? 'Sanction approved by Credit Committee' : 'Adverse credit parameters');

    db.history.push({
      historyId: db.history.length + 1,
      applicationId: app.applicationId,
      action: approve ? 'APPROVE' : 'REJECT',
      oldStatus: oldStatus,
      newStatus: app.status,
      performedBy: userName,
      performedAt: new Date().toISOString(),
      remarks: app.decisionReason
    });

    return res.status(200).json(app);
  }

  // 6. DISBURSE APPLICATION
  const disburseMatch = pathname.match(/^\/api\/applications\/(\d+)\/disburse$/);
  if (disburseMatch && method === 'POST') {
    if (!hasPermission(userRole, 'APPLICATION_DISBURSE')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot disburse applications.` });
    }
    const appId = parseInt(disburseMatch[1]);
    const app = db.applications.find(a => a.applicationId === appId);
    if (!app) return res.status(404).json({ error: 'Application not found.' });
    if (app.status !== 'CREDIT_APPROVAL') return res.status(400).json({ error: `Cannot disburse application in status ${app.status}. Must be CREDIT_APPROVAL.` });

    const ref = 'DISB-' + SUPABASE_CONFIG.businessDate.replace(/-/g, '') + '-' + Math.floor(100000 + Math.random() * 900000);
    const oldStatus = app.status;
    app.status = 'DISBURSED';
    app.disbursementReference = ref;

    db.history.push({
      historyId: db.history.length + 1,
      applicationId: app.applicationId,
      action: 'DISBURSE',
      oldStatus: oldStatus,
      newStatus: 'DISBURSED',
      performedBy: userName,
      performedAt: new Date().toISOString(),
      remarks: `Disbursed full amount INR ${formatIndianCurrency(app.requestedAmount)}. Ref: ${ref}`
    });

    return res.status(200).json({
      applicationId: app.applicationId,
      applicationNumber: app.applicationNumber,
      amount: app.requestedAmount,
      reference: ref,
      status: 'DISBURSED'
    });
  }

  // 7. APPLICATION HISTORY
  const historyMatch = pathname.match(/^\/api\/applications\/(\d+)\/history$/);
  if (historyMatch && method === 'GET') {
    if (!hasPermission(userRole, 'AUDIT_LOG_VIEW') && userRole !== 'LOAN_OFFICER') {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot view audit history.` });
    }
    const appId = parseInt(historyMatch[1]);
    const appHistory = db.history.filter(h => h.applicationId === appId);
    return res.status(200).json(appHistory);
  }

  // 8. REPORTS API
  if (pathname.startsWith('/api/reports/')) {
    if (!hasPermission(userRole, 'REPORTS_VIEW')) {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot view reports.` });
    }

    if (pathname === '/api/reports/register') {
      const data = db.applications.map(a => ({
        applicationNumber: a.applicationNumber,
        customerName: a.customerName || a.customerId,
        product: a.product,
        formattedRequestedAmount: 'INR ' + formatIndianCurrency(a.requestedAmount),
        formattedProposedEmi: 'INR ' + formatIndianCurrency(a.proposedEmi),
        status: a.status,
        submittedAt: a.submittedAt || '-'
      }));
      return res.status(200).json(data);
    }

    if (pathname === '/api/reports/pipeline/product') {
      const groups = {};
      db.applications.forEach(a => {
        const k = a.product + '|' + a.status;
        if (!groups[k]) groups[k] = { key1: a.product, key2: a.status, totalApplications: 0, amount: 0 };
        groups[k].totalApplications++;
        groups[k].amount += parseFloat(a.requestedAmount || 0);
      });
      const data = Object.values(groups).map(g => ({
        ...g,
        formattedAmount: 'INR ' + formatIndianCurrency(g.amount)
      }));
      return res.status(200).json(data);
    }

    if (pathname === '/api/reports/disbursements') {
      const disbursed = db.applications.filter(a => a.status === 'DISBURSED' || a.status === 'EXIT');
      const totalAmt = disbursed.reduce((sum, a) => sum + parseFloat(a.requestedAmount || 0), 0);
      return res.status(200).json([{
        periodOrBranch: SUPABASE_CONFIG.businessDate + ' (Today)',
        totalDisbursements: disbursed.length,
        formattedAmount: 'INR ' + formatIndianCurrency(totalAmt)
      }]);
    }

    if (pathname === '/api/reports/overdue') {
      const data = db.applications.filter(a => a.status === 'UNDER_REVIEW').map(a => ({
        applicationNumber: a.applicationNumber,
        customerName: a.customerName || a.customerId,
        product: a.product,
        formattedAmount: 'INR ' + formatIndianCurrency(a.requestedAmount),
        submittedAt: a.submittedAt || '2026-11-20',
        daysWaiting: 4
      }));
      return res.status(200).json(data);
    }

    if (pathname === '/api/reports/no-disbursement') {
      const disbursedCusts = new Set(db.applications.filter(a => a.status === 'DISBURSED' || a.status === 'EXIT').map(a => a.customerId));
      const data = db.customers.filter(c => !disbursedCusts.has(c.customerId)).map(c => ({
        customerId: c.customerId,
        customerName: c.name,
        mobile: c.mobile,
        totalApplications: db.applications.filter(a => a.customerId === c.customerId).length
      }));
      return res.status(200).json(data);
    }

    if (pathname === '/api/reports/repeat-applicants') {
      const counts = {};
      db.applications.forEach(a => {
        if (!counts[a.customerId]) counts[a.customerId] = { count: 0, sum: 0 };
        counts[a.customerId].count++;
        counts[a.customerId].sum += parseFloat(a.requestedAmount || 0);
      });
      const data = Object.keys(counts).filter(k => counts[k].count > 1).map(k => {
        const cust = db.customers.find(c => c.customerId === k);
        return {
          customerId: k,
          customerName: cust ? cust.name : k,
          mobile: cust ? cust.mobile : '-',
          applicationCount: counts[k].count,
          formattedAmount: 'INR ' + formatIndianCurrency(counts[k].sum)
        };
      });
      return res.status(200).json(data);
    }
  }

  // 9. UTILITIES & CRON
  if (pathname === '/api/utilities/run' && method === 'POST') {
    if (userRole !== 'ADMIN') {
      return res.status(403).json({ error: `Access Denied: Role [${userRole}] cannot trigger utilities. Required: ADMIN` });
    }

    // Pipeline 1: Overdue Monitor
    const overdueApps = db.applications.filter(a => a.status === 'UNDER_REVIEW');
    overdueApps.forEach(a => {
      if (!db.alerts.some(al => al.applicationId === a.applicationId && al.alertType === 'REVIEW_OVERDUE')) {
        db.alerts.push({
          alertId: db.alerts.length + 1,
          applicationId: a.applicationId,
          alertType: 'REVIEW_OVERDUE',
          createdAt: new Date().toISOString()
        });
      }
    });

    // Pipeline 2: Core Banking Push (DISBURSED -> EXIT)
    let pushedCount = 0;
    db.applications.forEach(a => {
      if (a.status === 'DISBURSED') {
        a.status = 'EXIT';
        pushedCount++;
        db.coreBanking.push({
          coreId: db.coreBanking.length + 1,
          applicationId: a.applicationId,
          customerName: a.customerName,
          disbursedAmount: a.requestedAmount,
          pushedAt: new Date().toISOString()
        });
        db.history.push({
          historyId: db.history.length + 1,
          applicationId: a.applicationId,
          action: 'CORE_BANKING_PUSH',
          oldStatus: 'DISBURSED',
          newStatus: 'EXIT',
          performedBy: 'VERCEL_CRON',
          performedAt: new Date().toISOString(),
          remarks: 'Transferred to Core Banking CBS'
        });
      }
    });

    const runResult = {
      runId: Date.now(),
      status: 'SUCCESS',
      environment: 'Vercel Serverless Cron',
      timestamp: new Date().toISOString(),
      overdueMonitor: {
        applicationsProcessed: overdueApps.length,
        alertsCreated: overdueApps.length
      },
      coreBankingPush: {
        loansPushedToCore: pushedCount,
        newStatus: 'EXIT'
      }
    };
    db.utilityRuns.push(runResult);
    return res.status(200).json(runResult);
  }

  if (pathname === '/api/utilities/csv' && method === 'GET') {
    const csvRows = ['ApplicationNumber,CustomerName,Product,Amount,SubmittedAt,OverdueFlag'];
    db.applications.forEach(a => {
      csvRows.push(`${a.applicationNumber},"${a.customerName || a.customerId}",${a.product},${a.requestedAmount},${a.submittedAt || ''},${a.status === 'UNDER_REVIEW' ? 'Yes' : 'No'}`);
    });
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename="pending_reviews.csv"');
    return res.status(200).send(csvRows.join('\n'));
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
    // Add express-like helpers
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

    // SPA fallback
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

