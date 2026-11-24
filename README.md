# Mini Loan Origination System (LOS) - Enterprise Core

A production-grade, dual-channel Mini Loan Origination System (LOS) built with **Java 21**, **Raw JDBC**, **PostgreSQL (Supabase Session Pooler on port 5432)**, and a **Vercel-Ready Serverless Web & REST Architecture**.

---

## 🚀 Instant Vercel Production Deployment

### Option A: Using Vercel CLI (Fastest)
From the root of this project, simply run:
```bash
npx vercel --prod
```
When prompted:
1. `Set up and deploy?` &rarr; `Y`
2. `Which scope?` &rarr; Select your Vercel account
3. `Link to existing project?` &rarr; `N`
4. `Project name?` &rarr; `mini-los-production` (or your choice)
5. `In which directory is your code located?` &rarr; `./`

### Option B: Push to GitHub & Connect to Vercel
1. Initialize git and commit:
   ```bash
   git init
   git add .
   git commit -m "feat: complete Vercel-ready Mini LOS system"
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
2. Go to [vercel.com/new](https://vercel.com/new) and import the repository.
3. In **Environment Variables**, add the Supabase keys:
   - `SUPABASE_URL`: `https://ciemfotaaupazeqxxuev.supabase.co`
   - `SUPABASE_SECRET_KEY`: `your_supabase_secret_key`
   - `SUPABASE_PUBLISHABLE_KEY`: `your_supabase_publishable_key`
   - `SUPABASE_JWKS_URL`: `https://ciemfotaaupazeqxxuev.supabase.co/auth/v1/.well-known/jwks.json`
4. Click **Deploy**. Your system is live at `https://<your-project>.vercel.app`!

---

## 💻 Local Testing (Two Options)

### 1. Instant Node / Vercel Serverless Simulator:
```bash
npm start
```
Opens at `http://localhost:3000` with full API endpoints, pre-seeded Indian banking records, and RBAC security.

### 2. Native Java 21 Enterprise CLI & REST Server:
```powershell
# Run the Interactive Menu-Driven Console:
java -cp "target/classes" com.los.console.ConsoleMain

# Run the 46-test Verification Suite:
java -cp "target/classes;target/test-classes" com.los.VerificationSuite
```

---

## 📐 Architecture & Standards

- **State Machine**: Strict non-reversible transitions (`DRAFT` &rarr; `UNDER_REVIEW` / `REJECTED` &rarr; `CREDIT_APPROVAL` &rarr; `DISBURSED` &rarr; `EXIT`).
- **Financial Calculations**:
  - EMI: $P \cdot r \cdot \frac{(1+r)^n}{(1+r)^n - 1}$ (P=₹1,00,000, r=12%, n=12 months yields exactly ₹8,884.88).
  - FOIR: $\frac{\text{Obligations} + \text{EMI}}{\text{Income}} \le 50.00\%$.
- **Dynamic RBAC (5 Roles)**:
  - `LOAN_OFFICER`: Onboard customers, originate applications, evaluate eligibility.
  - `CREDIT_OFFICER`: Review pipeline, approve / reject with audit rationale.
  - `DISBURSEMENT_OFFICER`: Authorize funds with globally unique RTGS references (`DISB-YYYYMMDD-XXXXXX`).
  - `AUDITOR`: Immutable read-only visibility into customers, loans, and audit trail logs.
  - `ADMIN`: Full authority including daily background pipelines.
- **Background Cron Pipelines**:
  - **Overdue Monitor**: Identifies `UNDER_REVIEW` applications older than 2 calendar days.
  - **Core Banking Push**: Idempotently transfers disbursed loans to Core Banking System CBS and transitions to `EXIT`.
- **6 Enterprise Parameterized SQL Reports**: Application Register, Product Pipeline, Disbursement Summary, Overdue Reviews, Customers Without Disbursement, Repeat Applicants.
