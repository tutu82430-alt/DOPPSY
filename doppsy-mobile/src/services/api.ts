import axios from 'axios'
import AsyncStorage from '@react-native-async-storage/async-storage'
import { Platform } from 'react-native'
import type { AuthResponse, DoctorResponse, BookingResponse, Specialty, RosterItemResponse } from '../types'

// ─── Base URL Resolution ──────────────────────────────────────────────────────
// Android Emulator maps host machine localhost to 10.0.2.2
// iOS Simulator / Web connect to localhost:8080 directly
const DEFAULT_URL = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080'
export const API_BASE = process.env.EXPO_PUBLIC_API_URL || DEFAULT_URL

// ─── Axios Instance ───────────────────────────────────────────────────────────
const api = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ─── Request Interceptor: Attach JWT Token ────────────────────────────────────
api.interceptors.request.use(
  async (config) => {
    try {
      const token = await AsyncStorage.getItem('doppsy_token')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    } catch (e) {
      console.warn('Failed to retrieve token from AsyncStorage', e)
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ─── API Helper Methods ───────────────────────────────────────────────────────

/**
 * Step 1 — Triggers mock/SMS OTP for mobile number.
 */
export async function sendOtp(mobileNumber: string): Promise<{ message: string; hint: string }> {
  const res = await api.post<{ message: string; hint: string }>('/api/v1/auth/send-otp', {
    mobileNumber,
  })
  return res.data
}

/**
 * Step 2 — Verifies OTP (default '123456') and stores JWT in AsyncStorage.
 */
export async function loginWithOtp(mobile: string, otp: string): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/api/v1/auth/verify-otp', {
    mobileNumber: mobile,
    otp,
  })
  const data = res.data

  // Persist token and user in AsyncStorage
  await AsyncStorage.setItem('doppsy_token', data.token)
  await AsyncStorage.setItem(
    'doppsy_user',
    JSON.stringify({
      userId: data.userId,
      fullName: data.fullName,
      role: data.role,
    })
  )

  return data
}

/**
 * Retrieves verified healthcare providers, optionally filtered by clinical specialty.
 */
export async function getDoctors(specialty?: Specialty | string): Promise<DoctorResponse[]> {
  const params: Record<string, string> = {}
  if (specialty && specialty !== 'ALL') {
    params.specialty = specialty
  }
  const res = await api.get<DoctorResponse[]>('/api/v1/providers', { params })
  return res.data
}

/**
 * Concurrency-safe zero-wait booking slot with pessimistic locking.
 */
export async function bookAppointment(
  providerId: string,
  timestamp: string
): Promise<BookingResponse> {
  const res = await api.post<BookingResponse>('/api/v1/appointments/book', {
    providerId,
    appointmentTimestamp: timestamp,
  })
  return res.data
}

/**
 * Fetches the authenticated doctor's full patient schedule for a specific date (defaults to today).
 * GET /api/v1/doctor/roster
 */
export async function getDoctorRoster(
  date?: string,
  providerId?: string
): Promise<RosterItemResponse[]> {
  const params: Record<string, string> = {}
  if (date) {
    params.date = date
  }
  if (providerId) {
    params.providerId = providerId
  }
  const res = await api.get<RosterItemResponse[]>('/api/v1/doctor/roster', { params })
  return res.data
}

/**
 * Updates an appointment's status on the doctor's roster (e.g. COMPLETED or NO_SHOW).
 * PATCH /api/v1/doctor/appointments/{id}/status
 */
export async function updateAppointmentStatus(
  id: string,
  status: 'COMPLETED' | 'CANCELLED' | 'NO_SHOW',
  providerId?: string
): Promise<RosterItemResponse> {
  const params: Record<string, string> = {}
  if (providerId) {
    params.providerId = providerId
  }
  const res = await api.patch<RosterItemResponse>(
    `/api/v1/doctor/appointments/${id}/status`,
    { status },
    { params }
  )
  return res.data
}

/**
 * Clears stored credentials from AsyncStorage.
 */
export async function logout(): Promise<void> {
  await AsyncStorage.multiRemove(['doppsy_token', 'doppsy_user'])
}

/**
 * Returns stored user profile from AsyncStorage.
 */
export async function getStoredUser(): Promise<{ userId: string; fullName: string; role: string } | null> {
  try {
    const raw = await AsyncStorage.getItem('doppsy_user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export default api
