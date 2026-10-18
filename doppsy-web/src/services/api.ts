import type {
  AuthResponse,
  BookingResponse,
  DoctorResponse,
  Specialty,
  RosterItemResponse,
  BlockSlotResponse,
} from '@/types'

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

/**
 * Verifies a Razorpay payment and promotes the appointment to BOOKED.
 * Called after the Razorpay checkout modal completes successfully.
 */
export async function verifyPayment(
  razorpayOrderId: string,
  razorpayPaymentId: string,
  razorpaySignature: string
): Promise<BookingResponse> {
  const res = await fetch(`${API_BASE}/api/v1/appointments/verify-payment`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ razorpayOrderId, razorpayPaymentId, razorpaySignature }),
  })
  return handleResponse<BookingResponse>(res)
}

// ─── Doctor Roster & Clinic Management ────────────────────────────────────────

/**
 * Fetches the authenticated doctor's full patient schedule for a specific date.
 * Defaults to today's date if no date string is provided.
 *
 * @param date optional ISO date string (YYYY-MM-DD)
 * @param providerId optional provider ID override
 */
export async function getDoctorRoster(date?: string, providerId?: string): Promise<RosterItemResponse[]> {
  const params = new URLSearchParams()
  if (date) params.set('date', date)
  if (providerId) params.set('providerId', providerId)

  const query = params.toString() ? `?${params.toString()}` : ''
  const headers = authHeaders()
  if (providerId) {
    (headers as Record<string, string>)['X-Provider-Id'] = providerId
  }

  const res = await fetch(`${API_BASE}/api/v1/doctor/roster${query}`, {
    headers,
  })
  return handleResponse<RosterItemResponse[]>(res)
}

/**
 * Updates an appointment's status on the doctor's roster (COMPLETED, CANCELLED, NO_SHOW).
 *
 * @param id appointment UUID
 * @param status target status
 * @param providerId optional provider ID override
 */
export async function updateAppointmentStatus(
  id: string,
  status: 'COMPLETED' | 'CANCELLED' | 'NO_SHOW',
  providerId?: string
): Promise<RosterItemResponse> {
  const params = providerId ? `?providerId=${providerId}` : ''
  const headers = authHeaders()
  if (providerId) {
    (headers as Record<string, string>)['X-Provider-Id'] = providerId
  }

  const res = await fetch(`${API_BASE}/api/v1/doctor/appointments/${id}/status${params}`, {
    method: 'PATCH',
    headers,
    body: JSON.stringify({ status }),
  })
  return handleResponse<RosterItemResponse>(res)
}

/**
 * Blocks out custom time intervals for emergencies, surgery, personal leave, or meetings.
 *
 * @param startTime ISO timestamp string (e.g. 2026-10-11T14:00:00)
 * @param endTime optional ISO timestamp string
 * @param reason optional note (e.g. 'Emergency Surgery')
 * @param providerId optional provider ID override
 */
export async function blockDoctorSlot(
  startTime: string,
  endTime?: string,
  reason?: string,
  providerId?: string
): Promise<BlockSlotResponse> {
  const params = providerId ? `?providerId=${providerId}` : ''
  const headers = authHeaders()
  if (providerId) {
    (headers as Record<string, string>)['X-Provider-Id'] = providerId
  }

  const res = await fetch(`${API_BASE}/api/v1/doctor/slots/block${params}`, {
    method: 'POST',
    headers,
    body: JSON.stringify({ startTime, endTime, reason }),
  })
  return handleResponse<BlockSlotResponse>(res)
}

/**
 * Fetches doctor profile information for header display.
 */
export async function getDoctorProfile(providerId?: string): Promise<DoctorResponse> {
  const params = providerId ? `?providerId=${providerId}` : ''
  const headers = authHeaders()
  if (providerId) {
    (headers as Record<string, string>)['X-Provider-Id'] = providerId
  }

  const res = await fetch(`${API_BASE}/api/v1/doctor/profile${params}`, {
    headers,
  })
  return handleResponse<DoctorResponse>(res)
}

// ─── WhatsApp Concierge Simulation & Testing ──────────────────────────────────

/**
 * Simulates an incoming patient WhatsApp message to test concierge cancellation or fallback.
 *
 * @param from patient's mobile number (e.g. '9876543210')
 * @param text message text (e.g. 'CANCEL')
 */
export async function simulateWhatsAppMessage(from: string, text: string): Promise<string> {
  const res = await fetch(`${API_BASE}/api/v1/whatsapp/webhook`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ from, text }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.text()
}

/**
 * Triggers the 24-hour reminder scan on demand.
 */
export async function triggerWhatsAppReminders(hours: number = 24): Promise<{ status: string; remindersSent: number }> {
  const res = await fetch(`${API_BASE}/api/v1/whatsapp/test-reminder?hours=${hours}`, {
    method: 'POST',
  })
  return handleResponse<{ status: string; remindersSent: number }>(res)
}


