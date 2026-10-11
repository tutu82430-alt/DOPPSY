# Doppsy Web — Zero-Wait Healthcare Frontend

Modern Next.js 14 frontend (App Router) for the **Doppsy** premium healthcare concierge platform.

## Features

- **Mobile OTP Authentication**: Clean login workflow with instant mock OTP support (`123456`) and JWT session persistence.
- **Doctor & Specialist Discovery**: Filter verified doctors, veterinarians, and dieticians by specialty and location.
- **Zero-Wait Appointment Booking**: Real-time slot selection and booking backed by database-level pessimistic locking.
- **Appointment Management**: View upcoming appointments, status badges, and cancel slots directly from the dashboard.
- **Dark Mode UI**: Built with Tailwind CSS, sleek glassmorphism, responsive cards, and Lucide React icons.

## Tech Stack

- **Framework**: Next.js 14 (App Router)
- **Styling**: Tailwind CSS
- **Icons**: Lucide React
- **Language**: TypeScript

## Getting Started

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Run local development server:**
   ```bash
   npm run dev
   ```

3. Open [http://localhost:3000](http://localhost:3000) in your browser.

Ensure the Spring Boot backend is running on `http://localhost:8080`. You can configure a custom API URL using `NEXT_PUBLIC_API_URL` environment variable if needed.
