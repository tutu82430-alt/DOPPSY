'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import {
  getDoctors,
  getMyAppointments,
  bookAppointment,
  cancelAppointment,
  getStoredUser,
  logout,
} from '@/services/api'
import type { DoctorResponse, BookingResponse, Specialty } from '@/types'
import {
  Activity,
  BadgeCheck,
  Calendar,
  ChevronDown,
  Clock,
  LogOut,
  MapPin,
  Search,
  Stethoscope,
  X,
  Loader2,
  AlertCircle,
  CheckCircle2,
  Star,
  Zap,
} from 'lucide-react'

// ─── Constants ────────────────────────────────────────────────────────────────
const SPECIALTIES: (Specialty | '')[] = [
  '', 'Allopathy', 'Ayurveda', 'Homeopathy',
  'Veterinary', 'Dietician', 'Dentistry',
  'Dermatology', 'Physiotherapy', 'Other',
]

const SPECIALTY_COLORS: Record<string, string> = {
  Allopathy:    'text-cyan-400 bg-cyan-500/10',
  Ayurveda:     'text-emerald-400 bg-emerald-500/10',
  Homeopathy:   'text-violet-400 bg-violet-500/10',
  Veterinary:   'text-rose-400 bg-rose-500/10',
  Dietician:    'text-amber-400 bg-amber-500/10',
  Dentistry:    'text-blue-400 bg-blue-500/10',
  Dermatology:  'text-pink-400 bg-pink-500/10',
  Physiotherapy:'text-orange-400 bg-orange-500/10',
  Other:        'text-slate-400 bg-slate-500/10',
}

function statusBadge(status: string) {
  const map: Record<string, string> = {
    BOOKED:    'badge-booked',
    CANCELLED: 'badge-cancelled',
    COMPLETED: 'badge-completed',
    AVAILABLE: 'badge-available',
  }
  return map[status] ?? 'badge'
}

function tierBadge(tier: string) {
  const map: Record<string, string> = {
    PREMIUM: 'badge-premium',
    BASIC:   'badge-basic',
    FREE:    'badge-free',
  }
  return map[tier] ?? 'badge'
}

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

// ─── Toast notification ───────────────────────────────────────────────────────
function Toast({ msg, type, onClose }: { msg: string; type: 'success' | 'error'; onClose: () => void }) {
  useEffect(() => { const t = setTimeout(onClose, 4000); return () => clearTimeout(t) }, [onClose])
  return (
    <div className={`fixed bottom-6 right-6 z-50 flex items-start gap-3 p-4 rounded-xl max-w-sm shadow-2xl animate-slide-up
      ${type === 'success' ? 'bg-emerald-500/15 border border-emerald-500/30' : 'bg-red-500/15 border border-red-500/30'}`}>
      {type === 'success'
        ? <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
        : <AlertCircle className="w-5 h-5 text-red-400 shrink-0 mt-0.5" />}
      <p className="text-sm text-white">{msg}</p>
      <button onClick={onClose} className="ml-auto shrink-0 text-slate-500 hover:text-white">
        <X className="w-4 h-4" />
      </button>
    </div>
  )
}

// ─── Book Modal ───────────────────────────────────────────────────────────────
function BookModal({
  doctor,
  onClose,
  onBooked,
}: {
  doctor: DoctorResponse
  onClose: () => void
  onBooked: (r: BookingResponse) => void
}) {
  const [timestamp, setTimestamp] = useState('')
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState('')

  const minDate = new Date(Date.now() + 60 * 60 * 1000).toISOString().slice(0, 16)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!timestamp) { setErr('Please select a date and time'); return }
    setErr('')
    setLoading(true)
    try {
      const res = await bookAppointment(doctor.providerId, timestamp + ':00')
      onBooked(res)
      onClose()
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Booking failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
      <div className="glass-card w-full max-w-md p-6 animate-slide-up" style={{ boxShadow: '0 30px 60px rgba(0,0,0,0.5)' }}>
        <div className="flex items-start justify-between mb-5">
          <div>
            <h3 className="text-lg font-bold text-white">{doctor.fullName}</h3>
            <p className="text-sm text-slate-400">{doctor.clinicName} · {doctor.location}</p>
          </div>
          <button onClick={onClose} className="text-slate-500 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={submit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-slate-400 uppercase tracking-wider">
              Appointment Date &amp; Time
            </label>
            <input
              id="appointment-datetime"
              type="datetime-local"
              min={minDate}
              value={timestamp}
              onChange={(e) => { setTimestamp(e.target.value); setErr('') }}
              className="input-field"
              required
            />
          </div>

          {/* Fee preview */}
          <div className="glass-card p-3 flex items-center justify-between">
            <span className="text-xs text-slate-400">Platform booking fee</span>
            <span className="text-sm font-semibold text-cyan-400">
              {doctor.subscriptionTier === 'PREMIUM' ? '₹ 50'
                : doctor.subscriptionTier === 'BASIC' ? '₹ 100' : '₹ 0'}
            </span>
          </div>

          {err && (
            <p className="text-xs text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">{err}</p>
          )}

          <button id="confirm-booking-btn" type="submit" disabled={loading} className="btn-primary w-full py-3">
            {loading ? <><Loader2 className="w-4 h-4 animate-spin" /> Booking…</> : <>Confirm Booking <Zap className="w-4 h-4" /></>}
          </button>
        </form>
      </div>
    </div>
  )
}

// ─── Doctor Card ──────────────────────────────────────────────────────────────
function DoctorCard({ doctor, onBook }: { doctor: DoctorResponse; onBook: () => void }) {
  const specColor = SPECIALTY_COLORS[doctor.specialty] ?? 'text-slate-400 bg-slate-500/10'
  return (
    <div className="glass-card-hover p-5 space-y-4">
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl bg-brand-gradient flex items-center justify-center text-white font-bold text-lg shrink-0">
            {doctor.fullName.charAt(doctor.fullName.indexOf(' ') + 1) || doctor.fullName[0]}
          </div>
          <div>
            <p className="font-semibold text-white text-sm">{doctor.fullName}</p>
            <p className="text-xs text-slate-500">{doctor.clinicName}</p>
          </div>
        </div>
        {doctor.isVerified && (
          <span title="Verified" className="shrink-0">
            <BadgeCheck className="w-5 h-5 text-cyan-400" />
          </span>
        )}
      </div>

      {/* Badges */}
      <div className="flex flex-wrap gap-2">
        <span className={`badge text-xs font-medium px-2.5 py-1 rounded-full ${specColor}`}>
          {doctor.specialty}
        </span>
        <span className={tierBadge(doctor.subscriptionTier)}>
          {doctor.subscriptionTier}
        </span>
        {doctor.isVerified && <span className="badge-verified">Verified</span>}
      </div>

      {/* Location */}
      <div className="flex items-center gap-1.5 text-xs text-slate-500">
        <MapPin className="w-3.5 h-3.5 shrink-0" />
        <span className="truncate">{doctor.location}</span>
      </div>

      {/* Rating placeholder */}
      <div className="flex items-center gap-1">
        {[...Array(5)].map((_, i) => (
          <Star key={i} className="w-3 h-3 text-amber-400 fill-amber-400" />
        ))}
        <span className="text-xs text-slate-500 ml-1">4.9 (124 reviews)</span>
      </div>

      <button
        id={`book-btn-${doctor.providerId}`}
        onClick={onBook}
        className="btn-primary w-full text-sm py-2.5"
      >
        Book Now <Calendar className="w-4 h-4" />
      </button>
    </div>
  )
}

// ─── Appointment Card ─────────────────────────────────────────────────────────
function AppointmentCard({
  appt,
  onCancel,
}: {
  appt: BookingResponse
  onCancel: (id: string) => void
}) {
  const [cancelling, setCancelling] = useState(false)

  async function handleCancel() {
    setCancelling(true)
    await onCancel(appt.appointmentId)
    setCancelling(false)
  }

  return (
    <div className="glass-card p-4 flex flex-col sm:flex-row sm:items-center gap-4">
      <div className="w-10 h-10 rounded-xl bg-brand-gradient flex items-center justify-center shrink-0">
        <Stethoscope className="w-5 h-5 text-white" />
      </div>

      <div className="flex-1 min-w-0">
        <p className="font-semibold text-white text-sm truncate">{appt.providerName}</p>
        <p className="text-xs text-slate-500 truncate">{appt.clinicName}</p>
        <div className="flex items-center gap-1.5 mt-1 text-xs text-slate-400">
          <Clock className="w-3 h-3" />
          {formatDate(appt.appointmentTimestamp)}
        </div>
      </div>

      <div className="flex items-center gap-3 shrink-0">
        <span className={statusBadge(appt.status)}>{appt.status}</span>
        {appt.status === 'BOOKED' && (
          <button
            id={`cancel-btn-${appt.appointmentId}`}
            onClick={handleCancel}
            disabled={cancelling}
            className="btn-ghost text-xs px-3 py-1.5 text-red-400 border-red-500/20 hover:bg-red-500/10"
          >
            {cancelling ? <Loader2 className="w-3 h-3 animate-spin" /> : 'Cancel'}
          </button>
        )}
      </div>
    </div>
  )
}

// ─── Dashboard Page ───────────────────────────────────────────────────────────
export default function DashboardPage() {
  const router = useRouter()
  const [user, setUser] = useState<{ fullName: string; role: string } | null>(null)

  const [doctors, setDoctors] = useState<DoctorResponse[]>([])
  const [appointments, setAppointments] = useState<BookingResponse[]>([])

  const [specialty, setSpecialty] = useState<Specialty | ''>('')
  const [location, setLocation] = useState('')
  const [searchInput, setSearchInput] = useState('')

  const [loadingDoctors, setLoadingDoctors] = useState(false)
  const [loadingAppts, setLoadingAppts] = useState(true)

  const [bookingDoctor, setBookingDoctor] = useState<DoctorResponse | null>(null)
  const [toast, setToast] = useState<{ msg: string; type: 'success' | 'error' } | null>(null)

  const showToast = (msg: string, type: 'success' | 'error') => setToast({ msg, type })

  // ── Auth guard ──────────────────────────────────────────────────────────────
  useEffect(() => {
    const u = getStoredUser()
    if (!u) { router.replace('/'); return }
    setUser(u)
  }, [router])

  // ── Load appointments on mount ──────────────────────────────────────────────
  useEffect(() => {
    if (!user) return
    setLoadingAppts(true)
    getMyAppointments()
      .then(setAppointments)
      .catch(() => showToast('Could not load appointments', 'error'))
      .finally(() => setLoadingAppts(false))
  }, [user])

  // ── Search doctors ──────────────────────────────────────────────────────────
  const searchDoctors = useCallback(async () => {
    setLoadingDoctors(true)
    try {
      const results = await getDoctors(specialty, searchInput)
      setDoctors(results)
    } catch {
      showToast('Search failed. Is the backend running?', 'error')
    } finally {
      setLoadingDoctors(false)
    }
  }, [specialty, searchInput])

  // Load all doctors on mount
  useEffect(() => { if (user) searchDoctors() }, [user, searchDoctors])

  // ── Handle booking success ──────────────────────────────────────────────────
  function handleBooked(res: BookingResponse) {
    setAppointments((prev) => [res, ...prev])
    showToast(`Appointment booked with ${res.providerName}!`, 'success')
  }

  // ── Handle cancel ───────────────────────────────────────────────────────────
  async function handleCancel(appointmentId: string) {
    try {
      const updated = await cancelAppointment(appointmentId)
      setAppointments((prev) =>
        prev.map((a) => (a.appointmentId === updated.appointmentId ? updated : a))
      )
      showToast('Appointment cancelled.', 'success')
    } catch (e: unknown) {
      showToast(e instanceof Error ? e.message : 'Cancel failed', 'error')
    }
  }

  // ── Handle logout ───────────────────────────────────────────────────────────
  function handleLogout() {
    logout()
    router.replace('/')
  }

  const upcomingAppts = appointments.filter((a) => a.status === 'BOOKED')
  const pastAppts     = appointments.filter((a) => a.status !== 'BOOKED')

  if (!user) return null

  return (
    <div className="page-bg min-h-screen flex flex-col">

      {/* ── Navbar ── */}
      <nav className="sticky top-0 z-30 border-b border-white/5 backdrop-blur-xl bg-black/20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-brand-gradient flex items-center justify-center">
              <Activity className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-white">Doppsy</span>
          </div>

          <div className="flex items-center gap-3">
            <Link
              href="/doctor"
              className="text-xs text-cyan-300 hover:text-white px-3 py-1.5 rounded-lg border border-cyan-500/30 hover:border-cyan-500 bg-cyan-500/10 transition-all flex items-center gap-1.5"
            >
              <Stethoscope className="w-3.5 h-3.5 text-cyan-400" />
              <span>Doctor Portal</span>
            </Link>
            <div className="hidden sm:flex flex-col text-right">
              <p className="text-sm font-medium text-white">{user.fullName}</p>
              <p className="text-xs text-slate-500">{user.role}</p>
            </div>
            <div className="w-9 h-9 rounded-full bg-brand-gradient flex items-center justify-center text-white text-sm font-bold">
              {user.fullName.charAt(0).toUpperCase()}
            </div>
            <button
              id="logout-btn"
              onClick={handleLogout}
              className="btn-ghost text-xs px-3 py-2"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
              <span className="hidden sm:inline">Logout</span>
            </button>
          </div>
        </div>
      </nav>

      <main className="flex-1 max-w-7xl mx-auto w-full px-4 sm:px-6 py-8 space-y-10 animate-fade-in">

        {/* ── Stats row ── */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {[
            { label: 'Upcoming',  value: upcomingAppts.length, icon: Calendar,   color: 'text-cyan-400',    bg: 'bg-cyan-500/10' },
            { label: 'Completed', value: appointments.filter(a=>a.status==='COMPLETED').length, icon: CheckCircle2, color: 'text-emerald-400', bg: 'bg-emerald-500/10' },
            { label: 'Cancelled', value: appointments.filter(a=>a.status==='CANCELLED').length, icon: X,           color: 'text-red-400',     bg: 'bg-red-500/10' },
            { label: 'Providers', value: doctors.length,        icon: Stethoscope, color: 'text-violet-400',  bg: 'bg-violet-500/10' },
          ].map(({ label, value, icon: Icon, color, bg }) => (
            <div key={label} className="glass-card p-4 flex items-center gap-3">
              <div className={`w-10 h-10 rounded-xl ${bg} flex items-center justify-center shrink-0`}>
                <Icon className={`w-5 h-5 ${color}`} />
              </div>
              <div>
                <p className="text-2xl font-bold text-white">{value}</p>
                <p className="text-xs text-slate-500">{label}</p>
              </div>
            </div>
          ))}
        </div>

        {/* ── Upcoming Appointments ── */}
        <section>
          <div className="flex items-center gap-2 mb-4">
            <Calendar className="w-5 h-5 text-cyan-400" />
            <h2 className="text-lg font-bold text-white">Upcoming Appointments</h2>
            {upcomingAppts.length > 0 && (
              <span className="badge-booked ml-1">{upcomingAppts.length}</span>
            )}
          </div>

          {loadingAppts ? (
            <div className="glass-card p-8 flex items-center justify-center gap-2 text-slate-400">
              <Loader2 className="w-5 h-5 animate-spin" /> Loading appointments…
            </div>
          ) : upcomingAppts.length === 0 ? (
            <div className="glass-card p-8 text-center">
              <Calendar className="w-10 h-10 text-slate-600 mx-auto mb-3" />
              <p className="text-slate-400 font-medium">No upcoming appointments</p>
              <p className="text-xs text-slate-600 mt-1">Search for a doctor below and book your first slot</p>
            </div>
          ) : (
            <div className="space-y-3">
              {upcomingAppts.map((a) => (
                <AppointmentCard key={a.appointmentId} appt={a} onCancel={handleCancel} />
              ))}
            </div>
          )}

          {/* Past appointments accordion */}
          {pastAppts.length > 0 && (
            <details className="mt-3 group">
              <summary className="text-xs text-slate-500 cursor-pointer hover:text-slate-300 transition-colors flex items-center gap-1 select-none">
                <ChevronDown className="w-3.5 h-3.5 group-open:rotate-180 transition-transform" />
                Show {pastAppts.length} past appointment{pastAppts.length > 1 ? 's' : ''}
              </summary>
              <div className="space-y-3 mt-3">
                {pastAppts.map((a) => (
                  <AppointmentCard key={a.appointmentId} appt={a} onCancel={handleCancel} />
                ))}
              </div>
            </details>
          )}
        </section>

        {/* ── Find a Doctor ── */}
        <section>
          <div className="flex items-center gap-2 mb-4">
            <Search className="w-5 h-5 text-violet-400" />
            <h2 className="text-lg font-bold text-white">Find a Doctor</h2>
          </div>

          {/* Search bar */}
          <div className="glass-card p-4 mb-6">
            <div className="flex flex-col sm:flex-row gap-3">
              {/* Specialty dropdown */}
              <div className="relative flex-1">
                <Stethoscope className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 pointer-events-none" />
                <select
                  id="specialty-select"
                  value={specialty}
                  onChange={(e) => setSpecialty(e.target.value as Specialty | '')}
                  className="input-field pl-9 appearance-none"
                >
                  <option value="">All Specialties</option>
                  {SPECIALTIES.filter(Boolean).map((s) => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 pointer-events-none" />
              </div>

              {/* Location search */}
              <div className="relative flex-1">
                <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 pointer-events-none" />
                <input
                  id="location-input"
                  type="text"
                  placeholder="City or area (e.g. Mumbai)"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && searchDoctors()}
                  className="input-field pl-9"
                />
              </div>

              <button
                id="search-doctors-btn"
                onClick={searchDoctors}
                disabled={loadingDoctors}
                className="btn-primary px-6 shrink-0"
              >
                {loadingDoctors
                  ? <Loader2 className="w-4 h-4 animate-spin" />
                  : <><Search className="w-4 h-4" /> Search</>}
              </button>
            </div>
          </div>

          {/* Results */}
          {loadingDoctors ? (
            <div className="glass-card p-12 flex items-center justify-center gap-2 text-slate-400">
              <Loader2 className="w-5 h-5 animate-spin" /> Searching providers…
            </div>
          ) : doctors.length === 0 ? (
            <div className="glass-card p-12 text-center">
              <Stethoscope className="w-10 h-10 text-slate-600 mx-auto mb-3" />
              <p className="text-slate-400 font-medium">No providers found</p>
              <p className="text-xs text-slate-600 mt-1">Try a different specialty or location</p>
            </div>
          ) : (
            <>
              <p className="text-xs text-slate-500 mb-4">
                {doctors.length} verified provider{doctors.length !== 1 ? 's' : ''} found
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {doctors.map((doc) => (
                  <DoctorCard
                    key={doc.providerId}
                    doctor={doc}
                    onBook={() => setBookingDoctor(doc)}
                  />
                ))}
              </div>
            </>
          )}
        </section>
      </main>

      {/* ── Book Modal ── */}
      {bookingDoctor && (
        <BookModal
          doctor={bookingDoctor}
          onClose={() => setBookingDoctor(null)}
          onBooked={handleBooked}
        />
      )}

      {/* ── Toast ── */}
      {toast && <Toast msg={toast.msg} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  )
}
