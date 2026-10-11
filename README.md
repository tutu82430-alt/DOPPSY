# Doppsy Healthcare Concierge

Premium healthcare concierge backend — zero-wait-time booking system for patients, doctors, and veterinarians.

## Project Structure

| File | Description |
|---|---|
| `schema.sql` | PostgreSQL DDL — full database schema with constraints & indexes |
| `schema.md` | Human-readable schema documentation |

## Quick Start

```bash
# Create the database
createdb doppsy_db

# Apply schema
psql -U postgres -d doppsy_db -f schema.sql
```

## Tech Stack

- **Database:** PostgreSQL 15+
- **Storage:** AWS S3 (health record documents)
- **Auth:** Mobile OTP (E.164 format)

---
*© 2026 Doppsy Healthcare Pvt. Ltd.*
