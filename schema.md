# Doppsy — PostgreSQL Database Schema

> **Version:** 1.0.0 · **Created:** 2026-10-11 · **Author:** Doppsy Engineering

---

## Overview

Doppsy is a premium healthcare concierge platform built for **zero-wait-time booking** across allopathy, ayurveda, veterinary, dietetics, and more. This document defines the canonical PostgreSQL relational schema for the core backend.

---

## Entity-Relationship Summary

```
users ──────────────────────────────────────────┐
  │ 1                                            │ 1
  │ has many                                     │ has many
  ▼ N                                            ▼ N
appointments ◄──── (N:1) ────► providers    health_records
```

| Relationship | Type | Description |
|---|---|---|
| `users` → `appointments` | One-to-Many | A patient can book many appointments |
| `providers` → `appointments` | One-to-Many | A provider owns many appointment slots |
| `users` → `health_records` | One-to-Many | A patient owns many health documents |
| `providers` → `health_records` | One-to-Many (nullable) | A provider may issue many records |

---

## Prerequisites

```sql
-- Required PostgreSQL extension for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

---

## Table Definitions

### 1. `users` — Patients & Admins

Stores all end-user accounts. Mobile number is the primary authentication identifier.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `user_id` | `UUID` | **PK**, `DEFAULT gen_random_uuid()` | Auto-generated surrogate key |
| `mobile_number` | `VARCHAR(15)` | `NOT NULL`, `UNIQUE` | E.164-format mobile; login identifier |
| `full_name` | `VARCHAR(255)` | `NOT NULL` | Display name |
| `age` | `INT` | `CHECK (age > 0 AND age < 150)` | Age in years |
| `role` | `VARCHAR(20)` | `NOT NULL`, `DEFAULT 'PATIENT'`, `CHECK IN ('PATIENT','ADMIN')` | Access control role |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT CURRENT_TIMESTAMP` | UTC account creation time |

**Indexes:** `idx_users_mobile` · `idx_users_role`

---

### 2. `providers` — Doctors, Vets, Dieticians

Onboarded healthcare providers across all supported specialties.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `provider_id` | `UUID` | **PK**, `DEFAULT gen_random_uuid()` | Auto-generated surrogate key |
| `full_name` | `VARCHAR(255)` | `NOT NULL` | Legal full name |
| `specialty` | `VARCHAR(100)` | `NOT NULL`, `CHECK IN (...)` | Clinical specialty |
| `clinic_name` | `VARCHAR(255)` | — | Name of clinic or hospital |
| `location` | `VARCHAR(500)` | — | Human-readable address or coordinates |
| `monthly_subscription_tier` | `VARCHAR(20)` | `NOT NULL`, `DEFAULT 'FREE'`, `CHECK IN ('FREE','BASIC','PREMIUM')` | Monetisation tier |
| `is_verified` | `BOOLEAN` | `NOT NULL`, `DEFAULT FALSE` | Credential verification flag |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT CURRENT_TIMESTAMP` | UTC onboarding time |

**Allowed `specialty` values:**
`Allopathy` · `Ayurveda` · `Homeopathy` · `Veterinary` · `Dietician` · `Dentistry` · `Dermatology` · `Physiotherapy` · `Other`

**Subscription Tiers:**

| Tier | Features |
|---|---|
| `FREE` | Basic listing, limited slots |
| `BASIC` | Priority listing, analytics dashboard |
| `PREMIUM` | Zero-commission, advanced analytics, verified badge |

**Indexes:** `idx_providers_specialty` · `idx_providers_location` · `idx_providers_tier` · `idx_providers_verified`

---

### 3. `appointments` — Core Transaction Table

Every bookable time slot is one row. The booking engine transitions rows through a strict lifecycle.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `appointment_id` | `UUID` | **PK**, `DEFAULT gen_random_uuid()` | Auto-generated surrogate key |
| `user_id` | `UUID` | **FK** → `users.user_id`, `ON DELETE SET NULL` | Patient; `NULL` when slot is AVAILABLE |
| `provider_id` | `UUID` | **FK** → `providers.provider_id`, `NOT NULL`, `ON DELETE RESTRICT` | Provider who owns the slot |
| `appointment_timestamp` | `TIMESTAMP` | `NOT NULL` | Scheduled UTC date/time |
| `status` | `VARCHAR(20)` | `NOT NULL`, `DEFAULT 'AVAILABLE'`, `CHECK IN ('AVAILABLE','BOOKED','COMPLETED','CANCELLED','NO_SHOW','BLOCKED')` | Slot lifecycle state |
| `booking_fee` | `DECIMAL(10,2)` | `CHECK (booking_fee >= 0)` | Fee in platform currency (INR) |
| `created_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT CURRENT_TIMESTAMP` | UTC creation time |

**Status Lifecycle:**

```
AVAILABLE ──► BOOKED ──► COMPLETED
                │        ├──► NO_SHOW
                │        └──► CANCELLED
                │
              BLOCKED (Held by Doctor/Clinic)
```

**Indexes:** `idx_appt_user_id` · `idx_appt_provider_id` · `idx_appt_status` · `idx_appt_timestamp`
**Composite:** `idx_appt_provider_status_ts (provider_id, status, appointment_timestamp)` — optimised for zero-wait booking queries

---

### 4. `health_records` — Documents & Prescriptions

Metadata for all patient health documents. Binary files are stored in AWS S3; only the URL is persisted.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `record_id` | `UUID` | **PK**, `DEFAULT gen_random_uuid()` | Auto-generated surrogate key |
| `user_id` | `UUID` | **FK** → `users.user_id`, `NOT NULL`, `ON DELETE CASCADE` | Owning patient |
| `provider_id` | `UUID` | **FK** → `providers.provider_id`, `ON DELETE SET NULL` | Issuing provider (nullable) |
| `document_url` | `VARCHAR(2048)` | `NOT NULL` | Pre-signed or permanent S3/CDN URL |
| `record_type` | `VARCHAR(50)` | `NOT NULL`, `CHECK IN (...)` | Document category |
| `uploaded_at` | `TIMESTAMP` | `NOT NULL`, `DEFAULT CURRENT_TIMESTAMP` | UTC upload time |

**Allowed `record_type` values:**
`PRESCRIPTION` · `LAB_REPORT` · `IMAGING` · `VACCINATION` · `OTHER`

**Indexes:** `idx_hr_user_id` · `idx_hr_provider_id` · `idx_hr_record_type` · `idx_hr_uploaded_at DESC`

---

## Referential Integrity Policy

| FK Relationship | ON DELETE | ON UPDATE | Rationale |
|---|---|---|---|
| `appointments.user_id` → `users` | `SET NULL` | `CASCADE` | Slot persists if user account is deleted |
| `appointments.provider_id` → `providers` | `RESTRICT` | `CASCADE` | Cannot delete provider with active slots |
| `health_records.user_id` → `users` | `CASCADE` | `CASCADE` | Records deleted with owning patient |
| `health_records.provider_id` → `providers` | `SET NULL` | `CASCADE` | Record preserved if provider is removed |

---

## Running the Schema

```bash
# Apply to a local PostgreSQL instance
psql -U postgres -d doppsy_db -f schema.sql

# Apply via Docker
docker exec -i <pg_container> psql -U postgres -d doppsy_db < schema.sql
```

---

## Future Considerations

- **`notifications` table** — push/SMS alerts for booking confirmations and reminders
- **`reviews` table** — patient feedback and star ratings per appointment
- **`payments` table** — payment gateway transaction ledger (Razorpay / Stripe)
- **`availability_slots` table** — provider-defined recurring availability windows
- **Row-Level Security (RLS)** — patient can only read their own records (Supabase/PostgREST)
- **Audit log** — `pgaudit` extension for HIPAA/compliance trails
- **Partitioning** — partition `appointments` by `appointment_timestamp` for scale

---

*© 2026 Doppsy Healthcare Pvt. Ltd. — All rights reserved.*
