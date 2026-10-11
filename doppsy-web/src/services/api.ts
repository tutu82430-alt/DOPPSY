import type { AuthResponse, BookingResponse, DoctorResponse, Specialty } from '@/types'

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

// ─── Internal helpers ─────────────────────────────────────────────────────────

function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem('doppsy_token')
}

function authHeaders(): HeadersInit {
  const token = getToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `HTTP ${res.status}`
    try {
      const err = await res.json()
      message = err.detail || err.message || message
    } catch {
      // ignore parse error
    }
    throw new Error(message)
  }
  return res.json() as Promise<T>
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

/**
 * Step 1 — Request an OTP for a mobile number.
 * In development the backend always accepts OTP "123456".
 */
export async function sendOtp(mobileNumber: string): Promise<{ message: string; hint: string }> {
  const res = await fetch(`${API_BASE}/api/v1/auth/send-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mobileNumber }),
  })
  return handleResponse(res)
}

/**
 * Step 2 — Verify OTP and receive a JWT bearer token.
 * Stores the token in localStorage on success.
 */
export async function login(mobileNumber: string, otp: string): Promise<AuthResponse> {
  const res = await fetch(`${API_BASE}/api/v1/auth/verify-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ mobileNumber, otp }),
  })
  const data = await handleResponse<AuthResponse>(res)
  // Persist auth state
  localStorage.setItem('doppsy_token', data.token)
  localStorage.setItem('doppsy_user', JSON.stringify({
    userId: data.userId,
    fullName: data.fullName,
    role: data.role,
  }))
  return data
}

/** Clears auth state from localStorage. */
export function logout(): void {
  localStorage.removeItem('doppsy_token')
  localStorage.removeItem('doppsy_user')
}

/** Returns the stored user profile, or null if not logged in. */
export function getStoredUser(): { userId: string; fullName: string; role: string } | null {
  if (typeof window === 'undefined') return null
  const raw = localStorage.getItem('doppsy_user')
  return raw ? JSON.parse(raw) : null
}

// ─── Providers ────────────────────────────────────────────────────────────────

/**
 * Fetches verified healthcare providers, optionally filtered by specialty and/or location.
 *
 * @example getDoctors('Allopathy', 'Mumbai')
 */
export async function getDoctors(specialty?: Specialty | '', location?: string): Promise<DoctorResponse[]> {
  const params = new URLSearchParams()
  if (specialty) params.set('specialty', specialty)
  if (location)  params.set('location', location)

  const query = params.toString() ? `?${params.toString()}` : ''
  const res = await fetch(`${API_BASE}/api/v1/providers${query}`, {
    headers: authHeaders(),
  })
  return handleResponse<DoctorResponse[]>(res)
}

// ─── Appointments ─────────────────────────────────────────────────────────────

/**
 * Books a time slot with a given provider.
 * JWT is automatically attached from localStorage.
 */
export async function bookAppointment(providerId: string, timestamp: string): Promise<BookingResponse> {
  const res = await fetch(`${API_BASE}/api/v1/appointments/book`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ providerId, appointmentTimestamp: timestamp }),
  })
  return handleResponse<BookingResponse>(res)
}

/**
 * Returns all appointments for the authenticated patient, newest first.
 */
export async function getMyAppointments(): Promise<BookingResponse[]> {
  const res = await fetch(`${API_BASE}/api/v1/appointments/me`, {
    headers: authHeaders(),
  })
  return handleResponse<BookingResponse[]>(res)
}

/**
 * Cancels a booked appointment.
 */
export async function cancelAppointment(appointmentId: string): Promise<BookingResponse> {
  const res = await fetch(`${API_BASE}/api/v1/appointments/${appointmentId}/cancel`, {
    method: 'PATCH',
    headers: authHeaders(),
  })
  return handleResponse<BookingResponse>(res)
}
