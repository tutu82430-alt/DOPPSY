-- =============================================================================
-- DOPPSY Healthcare Concierge — PostgreSQL Database Schema
-- Version  : 1.0.0
-- Created  : 2026-10-11
-- Author   : Doppsy Engineering
-- Description:
--   Core relational schema for a zero-wait-time booking system supporting
--   patients, doctors, veterinarians, dieticians, and health record storage.
-- =============================================================================

-- Enable the pgcrypto extension for UUID generation (gen_random_uuid())
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- TABLE: users
-- Stores all patient and admin accounts. Authentication is mobile-first.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile_number   VARCHAR(15)     NOT NULL UNIQUE,
    full_name       VARCHAR(255)    NOT NULL,
    age             INT             CHECK (age > 0 AND age < 150),
    role            VARCHAR(20)     NOT NULL DEFAULT 'PATIENT'
                                    CHECK (role IN ('PATIENT', 'ADMIN')),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  users                 IS 'Patient and admin accounts; mobile-first authentication.';
COMMENT ON COLUMN users.user_id         IS 'Surrogate primary key; UUID v4 auto-generated.';
COMMENT ON COLUMN users.mobile_number   IS 'E.164-style mobile number; used as login identifier.';
COMMENT ON COLUMN users.full_name       IS 'Display name of the user.';
COMMENT ON COLUMN users.age             IS 'Age in years; validated between 1 and 149.';
COMMENT ON COLUMN users.role            IS 'PATIENT: end user; ADMIN: platform operator.';
COMMENT ON COLUMN users.created_at      IS 'UTC timestamp of account creation.';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_mobile ON users (mobile_number);
CREATE INDEX IF NOT EXISTS idx_users_role   ON users (role);

-- -----------------------------------------------------------------------------
-- TABLE: providers
-- Doctors, veterinarians, dieticians, and other healthcare providers.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS providers (
    provider_id                 UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name                   VARCHAR(255)    NOT NULL,
    specialty                   VARCHAR(100)    NOT NULL
                                                CHECK (specialty IN (
                                                    'Allopathy', 'Ayurveda', 'Homeopathy',
                                                    'Veterinary', 'Dietician', 'Dentistry',
                                                    'Dermatology', 'Physiotherapy', 'Other'
                                                )),
    clinic_name                 VARCHAR(255),
    location                    VARCHAR(500),
    monthly_subscription_tier   VARCHAR(20)     NOT NULL DEFAULT 'FREE'
                                                CHECK (monthly_subscription_tier IN ('FREE', 'BASIC', 'PREMIUM')),
    is_verified                 BOOLEAN         NOT NULL DEFAULT FALSE,
    mobile_number               VARCHAR(15),
    created_at                  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  providers                             IS 'Healthcare providers: doctors, vets, dieticians, etc.';
COMMENT ON COLUMN providers.provider_id                 IS 'Surrogate primary key; UUID v4 auto-generated.';
COMMENT ON COLUMN providers.full_name                   IS 'Legal full name of the provider.';
COMMENT ON COLUMN providers.specialty                   IS 'Clinical specialty; constrained to approved values.';
COMMENT ON COLUMN providers.clinic_name                 IS 'Name of the clinic or hospital.';
COMMENT ON COLUMN providers.location                    IS 'Human-readable address or geo-coordinates string.';
COMMENT ON COLUMN providers.monthly_subscription_tier   IS 'Monetisation tier: FREE, BASIC, or PREMIUM.';
COMMENT ON COLUMN providers.is_verified                 IS 'TRUE once credentials are validated by the Doppsy team.';
COMMENT ON COLUMN providers.mobile_number               IS 'Registered mobile number for voice prescription ingestion.';
COMMENT ON COLUMN providers.created_at                  IS 'UTC timestamp of provider onboarding.';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_providers_specialty  ON providers (specialty);
CREATE INDEX IF NOT EXISTS idx_providers_location   ON providers (location);
CREATE INDEX IF NOT EXISTS idx_providers_tier       ON providers (monthly_subscription_tier);
CREATE INDEX IF NOT EXISTS idx_providers_verified   ON providers (is_verified);
CREATE INDEX IF NOT EXISTS idx_providers_mobile     ON providers (mobile_number);

-- -----------------------------------------------------------------------------
-- TABLE: appointments
-- Core transaction table driving the zero-wait-time booking engine.
-- Each row represents one bookable slot.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS appointments (
    appointment_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID            REFERENCES users(user_id)
                                            ON DELETE SET NULL ON UPDATE CASCADE,
    provider_id             UUID            NOT NULL
                                            REFERENCES providers(provider_id)
                                            ON DELETE RESTRICT ON UPDATE CASCADE,
    appointment_timestamp   TIMESTAMP       NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'AVAILABLE'
                                            CHECK (status IN (
                                                'AVAILABLE', 'PENDING_PAYMENT', 'BOOKED',
                                                'COMPLETED', 'CANCELLED', 'NO_SHOW', 'BLOCKED'
                                            )),
    booking_fee             DECIMAL(10, 2)  CHECK (booking_fee >= 0),
    razorpay_order_id       VARCHAR(50),
    razorpay_payment_id     VARCHAR(50),
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  appointments                          IS 'Bookable appointment slots; the core transaction entity.';
COMMENT ON COLUMN appointments.appointment_id           IS 'Surrogate primary key; UUID v4 auto-generated.';
COMMENT ON COLUMN appointments.user_id                  IS 'FK to users; NULL for AVAILABLE (unbooked) slots.';
COMMENT ON COLUMN appointments.provider_id              IS 'FK to providers; provider who owns this slot.';
COMMENT ON COLUMN appointments.appointment_timestamp    IS 'Scheduled date and time of the appointment (UTC).';
COMMENT ON COLUMN appointments.status                   IS 'Lifecycle state: AVAILABLE → BOOKED → COMPLETED | CANCELLED.';
COMMENT ON COLUMN appointments.booking_fee              IS 'Fee charged to the patient in INR (or platform currency).';
COMMENT ON COLUMN appointments.created_at               IS 'UTC timestamp when the slot was first created.';

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_appt_user_id             ON appointments (user_id);
CREATE INDEX IF NOT EXISTS idx_appt_provider_id         ON appointments (provider_id);
CREATE INDEX IF NOT EXISTS idx_appt_status              ON appointments (status);
CREATE INDEX IF NOT EXISTS idx_appt_timestamp           ON appointments (appointment_timestamp);
-- Composite: find all AVAILABLE slots for a provider ordered by time
CREATE INDEX IF NOT EXISTS idx_appt_provider_status_ts  ON appointments (provider_id, status, appointment_timestamp);

-- -----------------------------------------------------------------------------
-- TABLE: health_records
-- Stores metadata/links to prescriptions, lab reports, and other documents.
-- Binary files are stored in AWS S3; only the URL is persisted here.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS health_records (
    record_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL
                                    REFERENCES users(user_id)
                                    ON DELETE CASCADE ON UPDATE CASCADE,
    provider_id     UUID            REFERENCES providers(provider_id)
                                    ON DELETE SET NULL ON UPDATE CASCADE,
    document_url    VARCHAR(2048)   NOT NULL,
    record_type     VARCHAR(50)     NOT NULL
                                    CHECK (record_type IN (
                                        'PRESCRIPTION', 'LAB_REPORT',
                                        'IMAGING', 'VACCINATION', 'OTHER'
                                    )),
    uploaded_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  health_records                IS 'Metadata for patient health documents stored externally (S3).';
COMMENT ON COLUMN health_records.record_id      IS 'Surrogate primary key; UUID v4 auto-generated.';
COMMENT ON COLUMN health_records.user_id        IS 'FK to users; the patient who owns this record.';
COMMENT ON COLUMN health_records.provider_id    IS 'FK to providers; the issuing provider (nullable).';
COMMENT ON COLUMN health_records.document_url   IS 'Pre-signed or permanent S3/CDN URL to the document.';
COMMENT ON COLUMN health_records.record_type    IS 'Category: PRESCRIPTION, LAB_REPORT, IMAGING, VACCINATION, OTHER.';
COMMENT ON COLUMN health_records.uploaded_at    IS 'UTC timestamp when the document was uploaded.';

-- Indexes
CREATE INDEX IF NOT EXISTS idx_hr_user_id       ON health_records (user_id);
CREATE INDEX IF NOT EXISTS idx_hr_provider_id   ON health_records (provider_id);
CREATE INDEX IF NOT EXISTS idx_hr_record_type   ON health_records (record_type);
CREATE INDEX IF NOT EXISTS idx_hr_uploaded_at   ON health_records (uploaded_at DESC);

-- -----------------------------------------------------------------------------
-- TABLE: waitlists
-- Smart Waitlist Engine: tracks patients queuing for cancellations with a specific doctor and date.
-- When a slot opens, the top 3 waiting patients are notified to CLAIM it.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS waitlists (
    waitlist_id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID            NOT NULL
                                            REFERENCES users(user_id)
                                            ON DELETE CASCADE ON UPDATE CASCADE,
    provider_id             UUID            NOT NULL
                                            REFERENCES providers(provider_id)
                                            ON DELETE CASCADE ON UPDATE CASCADE,
    target_date             DATE            NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'WAITING'
                                            CHECK (status IN ('WAITING', 'NOTIFIED', 'CLAIMED', 'EXPIRED', 'CANCELLED')),
    notified_slot_id        UUID            REFERENCES appointments(appointment_id)
                                            ON DELETE SET NULL ON UPDATE CASCADE,
    notified_at             TIMESTAMP,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  waitlists                     IS 'Smart Waitlist: patient FIFO queues for cancelled provider slots.';
COMMENT ON COLUMN waitlists.waitlist_id         IS 'Surrogate primary key; UUID v4 auto-generated.';
COMMENT ON COLUMN waitlists.user_id             IS 'FK to users; the patient waiting for an opening.';
COMMENT ON COLUMN waitlists.provider_id         IS 'FK to providers; the doctor requested.';
COMMENT ON COLUMN waitlists.target_date         IS 'Specific calendar date requested by the patient.';
COMMENT ON COLUMN waitlists.status              IS 'WAITING (queue) -> NOTIFIED (claim in-flight) -> CLAIMED | EXPIRED | CANCELLED.';
COMMENT ON COLUMN waitlists.notified_slot_id    IS 'FK to appointments; slot offered to this patient.';
COMMENT ON COLUMN waitlists.notified_at         IS 'Timestamp when the opening alert was dispatched via WhatsApp.';
COMMENT ON COLUMN waitlists.created_at          IS 'Timestamp when the patient joined the waitlist (determines FIFO priority).';

-- Indexes for waitlist prioritization & lookup
CREATE INDEX IF NOT EXISTS idx_waitlist_provider_date_status ON waitlists (provider_id, target_date, status, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_waitlist_user_status          ON waitlists (user_id, status);
CREATE INDEX IF NOT EXISTS idx_waitlist_notified_slot        ON waitlists (notified_slot_id);

-- =============================================================================
-- END OF SCHEMA
-- =============================================================================

