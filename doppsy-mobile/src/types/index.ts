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
  | 'PENDING_PAYMENT'
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
  /** Razorpay order ID — present when status is PENDING_PAYMENT */
  razorpayOrderId?: string
  /** Razorpay publishable key ID — present when status is PENDING_PAYMENT */
  razorpayKeyId?: string
}

/** Request body for verifying a Razorpay payment on mobile */
export interface VerifyPaymentRequest {
  razorpayOrderId: string
  razorpayPaymentId: string
  razorpaySignature: string
}

// ─── Doctor Roster & Clinic Management Types ──────────────────────────────────

export interface RosterItemResponse {
  appointmentId: string
  appointmentTimestamp: string
  status: AppointmentStatus
  patientName: string | null
  patientAge: number | null
  patientContact: string | null
  patientUserId?: string | null
  bookingFee: number
  createdAt?: string
}

export interface UpdateAppointmentStatusRequest {
  status: 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'
}
