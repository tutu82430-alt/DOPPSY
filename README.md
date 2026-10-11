# Doppsy Healthcare Concierge

Premium healthcare concierge platform — zero-wait-time booking system for patients, doctors, veterinarians, and dieticians.

---

## Architecture Overview

```
                      ┌───────────────────────────────────────┐
                      │          doppsy-web (Next.js 14)      │
                      │  Tailwind CSS + App Router (Port 3000)│
                      └──────────────────┬────────────────────┘
                                         │ REST (JSON + JWT)
                                         ▼
                      ┌───────────────────────────────────────┐
                      │      Doppsy Backend (Spring Boot 3)   │
                      │        Java 17 / Maven (Port 8080)    │
                      └──────────────────┬────────────────────┘
                                         │ JDBC / JPA
                                         ▼
                      ┌───────────────────────────────────────┐
                      │      PostgreSQL (Docker Container)    │
                      │         Database: doppsy:5432         │
                      └───────────────────────────────────────┘
```

---

## Repository Structure

| Directory / File | Description |
|---|---|
| `docker-compose.yml` | PostgreSQL container definition (`doppsy-db`) |
| `schema.sql` | PostgreSQL DDL with indexes, foreign keys, and constraints |
| `schema.md` | Complete database schema documentation and ER diagrams |
| `src/main/java/com/doppsy/` | Spring Boot 3 backend application code |
| ├── `controller/` | REST API endpoints (`AuthController`, `ProviderController`, `AppointmentController`, `DoctorController`, `WhatsAppWebhookController`) |
| ├── `service/` | Business logic & transaction management (`AuthService`, `ProviderService`, `AppointmentService`, `DoctorService`, `WhatsAppService`, `WhatsAppWebhookService`) |
| ├── `jobs/` | Background scheduled jobs (`AppointmentReminderJob` — hourly 24h reminders) |
| ├── `repository/` | Spring Data JPA repositories with `@Lock(PESSIMISTIC_WRITE)` concurrency protection |
| ├── `entity/` | JPA Entities (`User`, `Provider`, `Appointment`) |
| ├── `dto/` | Request / Response data transfer objects (`RosterItemResponse`, `BlockSlotRequest`, etc.) |
| └── `config/` | Data seeder with sample providers and security / CORS configuration |
| `doppsy-web/` | Next.js 14 App Router frontend with Tailwind CSS & Lucide icons |
| ├── `src/app/page.tsx` | Mobile OTP authentication landing page |
| ├── `src/app/dashboard/` | Doctor discovery & appointment booking dashboard |
| ├── `src/app/doctor/` | Clinic & Doctor Operations dashboard (Roster queue & Slot blocking) |
| └── `src/services/api.ts` | Centralized API client connecting to Spring Boot backend |
| `doppsy-mobile/` | React Native iOS & Android application with Expo Router |
| ├── `app/index.tsx` | Mobile OTP authentication screen |
| ├── `app/dashboard/index.tsx` | Patient specialist discovery & zero-wait booking screen |
| └── `src/services/api.ts` | Axios client with AsyncStorage JWT interceptor |

---

## WhatsApp Concierge & Automated Reminders

- **Meta Webhook Handshake**: `GET /api/v1/whatsapp/webhook` verifies `hub.mode` and `hub.verify_token`, returning `hub.challenge`.
- **Inbound Message Processing**: `POST /api/v1/whatsapp/webhook` ingests patient WhatsApp replies.
  - Replying **`CANCEL`** cancels the patient's most recent `BOOKED` appointment and replies with a confirmation.
  - Other queries trigger a concierge fallback: *"DOPSY Concierge: We have received your message. A human assistant will reply shortly."*
- **Hourly 24-Hour Reminder Cron**: `AppointmentReminderJob` runs `@Scheduled(cron = "0 0 * * * *")` querying upcoming booked slots and sending reminders with reply-to-cancel capability.
- **Manual Reminder Trigger**: `POST /api/v1/whatsapp/test-reminder?hours=24` triggers immediate scanning and dispatching.

---

## Quick Start

### 1. Database Setup
```bash
docker-compose up -d
```

### 2. Spring Boot Backend
```bash
mvn clean spring-boot:run
```
The backend starts on `http://localhost:8080` with sample seed data preloaded.

### 3. Frontend Web App
```bash
cd doppsy-web
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000).

---

## Production Cloud Deployment (Docker & Nginx)

Run the full production stack (PostgreSQL, Spring Boot Backend, Next.js Frontend, and Nginx Gateway):

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

- **Public Gateway**: [http://localhost](http://localhost) (Port 80/443 via Nginx)
- **API Routing**: `/api/*` seamlessly proxied to the internal Spring Boot container (`backend:8080`)
- **Web App**: `/*` served by the Next.js production server (`frontend:3000`)
- **Isolated Network**: PostgreSQL and internal application ports are locked down and never exposed to the host machine.

---

## Authentication & Test Credentials

- **Default Test OTP**: `123456`
- **Supported Roles**: `PATIENT`, `ADMIN`
- **Specialties**: Allopathy, Ayurveda, Veterinary, Dietician, Dentistry, Dermatology, etc.

---
*© 2026 Doppsy Healthcare Pvt. Ltd.*
