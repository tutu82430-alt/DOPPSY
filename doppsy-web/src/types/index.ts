// ─── Auth ─────────────────────────────────────────────────────────────────────

export type UserRole = 'PATIENT' | 'ADMIN'

export interface AuthResponse {
  token: string
  userId: string
  fullName: string
  role: UserRole
  newUser: boolean
}

// ─── Providers ────────────────────────────────────────────────────────────────

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

// ─── Appointments ─────────────────────────────────────────────────────────────

export type AppointmentStatus = 'AVAILABLE' | 'BOOKED' | 'COMPLETED' | 'CANCELLED'

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

export interface BookingRequest {
  providerId: string
  appointmentTimestamp: string
}
