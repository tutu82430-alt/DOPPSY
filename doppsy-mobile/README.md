# Doppsy Mobile — iOS & Android Healthcare Concierge App

Native mobile application for the **Doppsy** zero-wait healthcare concierge platform built with **React Native** and **Expo Router**.

---

## Features

- **Mobile OTP Authentication**: Clean login workflow with instant mock OTP support (`123456`) and secure JWT session persistence using `@react-native-async-storage/async-storage`.
- **Specialist Discovery**: Real-time browsing and filtering of verified doctors, veterinarians, and dieticians by clinical specialty.
- **Zero-Wait Concurrency Booking**: Instant slot booking powered by PostgreSQL pessimistic row locking.
- **WhatsApp Concierge Confirmation**: Native alert on confirmation indicating WhatsApp delivery and 24-hour automated reminder capability.
- **Expo Router Navigation**: Modern file-based routing (`/` -> `/dashboard`).

---

## Tech Stack

- **Framework**: React Native with Expo SDK 51 & Expo Router
- **HTTP Client**: Axios with automatic JWT token interceptor
- **Storage**: `@react-native-async-storage/async-storage`
- **UI & Layout**: React Native `StyleSheet` with dark luxury palette & `react-native-safe-area-context`

---

## Getting Started

1. **Install dependencies:**
   ```bash
   cd doppsy-mobile
   npm install
   ```

2. **Start the Expo development server:**
   ```bash
   npx expo start
   ```

3. **Run on a device or emulator:**
   - **Android Emulator**: Press `a` in the terminal (connects to `http://10.0.2.2:8080`)
   - **iOS Simulator**: Press `i` in the terminal (connects to `http://localhost:8080`)
   - **Physical Device**: Scan the QR code using the **Expo Go** app on iOS or Android (ensure `EXPO_PUBLIC_API_URL` is set to your machine's LAN IP, e.g. `http://192.168.1.x:8080`).

---

## Environment Configuration

Optionally configure a custom backend URL in `.env`:

```env
EXPO_PUBLIC_API_URL=http://10.0.2.2:8080
```
