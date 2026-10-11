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
| ├── `controller/` | REST API endpoints (`AuthController`, `ProviderController`, `AppointmentController`, `DoctorController`) |
| ├── `service/` | Business logic & transaction management (`AuthService`, `ProviderService`, `AppointmentService`, `DoctorService`) |
| ├── `repository/` | Spring Data JPA repositories with `@Lock(PESSIMISTIC_WRITE)` concurrency protection |
| ├── `entity/` | JPA Entities (`User`, `Provider`, `Appointment`) |
| ├── `dto/` | Request / Response data transfer objects (`RosterItemResponse`, `BlockSlotRequest`, etc.) |
| └── `config/` | Data seeder with sample providers and security / CORS configuration |
| `doppsy-web/` | Next.js 14 App Router frontend with Tailwind CSS & Lucide icons |
| ├── `src/app/page.tsx` | Mobile OTP authentication landing page |
| ├── `src/app/dashboard/` | Doctor discovery & appointment booking dashboard |
| ├── `src/app/doctor/` | Clinic & Doctor Operations dashboard (Roster queue & Slot blocking) |
| └── `src/services/api.ts` | Centralized API client connecting to Spring Boot backend |

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

## Authentication & Test Credentials

- **Default Test OTP**: `123456`
- **Supported Roles**: `PATIENT`, `ADMIN`
- **Specialties**: Allopathy, Ayurveda, Veterinary, Dietician, Dentistry, Dermatology, etc.

---
*© 2026 Doppsy Healthcare Pvt. Ltd.*
