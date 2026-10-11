// ─── Authentication Types ───────────────────────────────────────────────────────

export type UserRole = 'PATIENT' | 'ADMIN'

export interface AuthResponse {
  token: string
  userId: string
  fullName: string
  role: UserRole
  newUser: boolean
}

// ─── Doctor & Provider Types ──────────────────────────────────────────────────

export type Specialty =
  | 'Allopathy'
  | 'Ayurveda'
  | 'Homeopathy'
  | 'Veterinary'
  | 'Dietician'
  | 'Dentistry'
  | 'Dermatology'
  | 'Physiotherapy'
  | 'Other'

export type SubscriptionTier = 'FREE' | 'BASIC' | 'PREMIUM'

export interface DoctorResponse {
  providerId: string
  fullName: string
  specialty: Specialty
  clinicName: string
  location: string
  subscriptionTier: SubscriptionTier
  isVerified: boolean
}

// ─── Appointment Types ────────────────────────────────────────────────────────

export type AppointmentStatus =
  | 'AVAILABLE'
  | 'BOOKED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'BLOCKED'

export interface BookingResponse {
  appointmentId: string
  providerId: string
  providerName: string
  clinicName: string
  appointmentTimestamp: string
  status: AppointmentStatus
  bookingFee: number
  createdAt: string
}
