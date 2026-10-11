'use client'

import { useState, useEffect, useCallback, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import {
  getDoctorRoster,
  getDoctorProfile,
  updateAppointmentStatus,
  blockDoctorSlot,
  getStoredUser,
  logout,
} from '@/services/api'
import type { RosterItemResponse, DoctorResponse, AppointmentStatus } from '@/types'
import {
  Activity,
  AlertCircle,
  BadgeCheck,
  Calendar,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Filter,
  Lock,
  LogOut,
  MapPin,
  Phone,
  RefreshCw,
  Search,
  ShieldAlert,
  Stethoscope,
  User,
  UserCheck,
  UserX,
  Users,
  X,
  Zap,
} from 'lucide-react'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    })
  } catch {
    return iso
  }
}

function formatDateHeader(dateStr: string) {
  try {
    const d = new Date(dateStr + 'T00:00:00')
    return d.toLocaleDateString('en-IN', {
      weekday: 'long',
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  } catch {
    return dateStr
  }
}

function statusBadge(status: AppointmentStatus) {
  switch (status) {
    case 'BOOKED':
      return 'badge-booked'
    case 'COMPLETED':
      return 'badge-completed'
    case 'NO_SHOW':
      return 'badge-noshow'
    case 'CANCELLED':
      return 'badge-cancelled'
    case 'BLOCKED':
      return 'badge-blocked'
    case 'AVAILABLE':
      return 'badge-available'
    default:
      return 'badge'
  }
}

// ─── Toast Component ──────────────────────────────────────────────────────────

function Toast({
  msg,
  type,
  onClose,
}: {
  msg: string
  type: 'success' | 'error'
  onClose: () => void
}) {
  useEffect(() => {
    const t = setTimeout(onClose, 4000)
    return () => clearTimeout(t)
  }, [onClose])

  return (
    <div
      className={`fixed bottom-6 right-6 z-50 flex items-start gap-3 p-4 rounded-xl max-w-md shadow-2xl animate-slide-up backdrop-blur-xl border ${
        type === 'success'
          ? 'bg-emerald-950/80 border-emerald-500/40 text-emerald-200'
          : 'bg-red-950/80 border-red-500/40 text-red-200'
      }`}
    >
      {type === 'success' ? (
        <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
      ) : (
        <AlertCircle className="w-5 h-5 text-red-400 shrink-0 mt-0.5" />
      )}
      <p className="text-sm font-medium">{msg}</p>
      <button onClick={onClose} className="ml-auto text-slate-400 hover:text-white transition-colors">
        <X className="w-4 h-4" />
      </button>
    </div>
  )
}

// ─── Block Hours Modal ────────────────────────────────────────────────────────

function BlockHoursModal({
  selectedDate,
  onClose,
  onBlocked,
}: {
  selectedDate: string
  onClose: () => void
  onBlocked: (msg: string) => void
}) {
  const [startTime, setStartTime] = useState(`${selectedDate}T14:00`)
  const [endTime, setEndTime] = useState(`${selectedDate}T16:00`)
  const [reason, setReason] = useState('Emergency Surgery')
  const [customReason, setCustomReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [err, setErr] = useState('')

  const PRESET_REASONS = [
    'Emergency Surgery',
    'Clinical Procedure',
    'Hospital Rounds',
    'Personal Leave',
    'Lunch / Break',
    'Other',
  ]

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErr('')
    setSubmitting(true)
    try {
      const finalReason = reason === 'Other' ? (customReason.trim() || 'Blocked Interval') : reason
      const startIso = `${startTime}:00`
      const endIso = `${endTime}:00`

      const res = await blockDoctorSlot(startIso, endIso, finalReason)
      onBlocked(res.message || 'Time interval successfully blocked.')
      onClose()
    } catch (error: unknown) {
      setErr(error instanceof Error ? error.message : 'Failed to block slots')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fade-in">
      <div className="glass-card w-full max-w-lg p-6 rounded-2xl border border-white/10 shadow-2xl space-y-5 bg-[#0d1526]/95">
        <div className="flex items-center justify-between border-b border-white/10 pb-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
              <Lock className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-white">Block Clinic Hours</h3>
              <p className="text-xs text-slate-400">Mark intervals as unavailable for patient bookings</p>
            </div>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        {err && (
          <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 flex items-center gap-2 text-xs text-red-300">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{err}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">Start Time</label>
              <input
                type="datetime-local"
                value={startTime}
                onChange={(e) => setStartTime(e.target.value)}
                required
                className="input-field text-sm"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-300 mb-1">End Time</label>
              <input
                type="datetime-local"
                value={endTime}
                onChange={(e) => setEndTime(e.target.value)}
                required
                className="input-field text-sm"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 mb-1">Reason for Hold</label>
            <div className="grid grid-cols-2 gap-2 mb-2">
              {PRESET_REASONS.map((r) => (
                <button
                  type="button"
                  key={r}
                  onClick={() => setReason(r)}
                  className={`px-3 py-2 text-xs rounded-lg font-medium border text-left transition-all ${
                    reason === r
                      ? 'border-cyan-500 bg-cyan-500/15 text-cyan-300'
                      : 'border-white/10 bg-white/5 text-slate-400 hover:text-white'
                  }`}
                >
                  {r}
                </button>
              ))}
            </div>

            {reason === 'Other' && (
              <input
                type="text"
                placeholder="Specify custom reason..."
                value={customReason}
                onChange={(e) => setCustomReason(e.target.value)}
                required
                className="input-field text-sm mt-2"
              />
            )}
          </div>

          <div className="flex items-center gap-3 pt-3 border-t border-white/10">
            <button
              type="button"
              onClick={onClose}
              className="btn-ghost flex-1 py-2.5 text-xs text-slate-300"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="btn-primary flex-1 py-2.5 text-xs text-white"
            >
              {submitting ? 'Blocking...' : 'Confirm Block'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Main Doctor Dashboard Page ───────────────────────────────────────────────

export default function DoctorDashboard() {
  const router = useRouter()

  // State
  const [profile, setProfile] = useState<DoctorResponse | null>(null)
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().split('T')[0])
  const [roster, setRoster] = useState<RosterItemResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null)
  const [filterStatus, setFilterStatus] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState('')
  const [showBlockModal, setShowBlockModal] = useState(false)
  const [toast, setToast] = useState<{ msg: string; type: 'success' | 'error' } | null>(null)

  // Auth check and load
  useEffect(() => {
    const user = getStoredUser()
    if (!user) {
      router.push('/')
    }
  }, [router])

  // Load profile & roster
  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [docProfile, docRoster] = await Promise.all([
        getDoctorProfile().catch(() => null),
        getDoctorRoster(selectedDate),
      ])
      if (docProfile) setProfile(docProfile)
      setRoster(docRoster)
    } catch (err: unknown) {
      setToast({
        msg: err instanceof Error ? err.message : 'Failed to fetch doctor schedule',
        type: 'error',
      })
    } finally {
      setLoading(false)
    }
  }, [selectedDate])

  useEffect(() => {
    loadData()
  }, [loadData])

  // Date controls
  function handlePrevDay() {
    const cur = new Date(selectedDate + 'T00:00:00')
    cur.setDate(cur.getDate() - 1)
    setSelectedDate(cur.toISOString().split('T')[0])
  }

  function handleNextDay() {
    const cur = new Date(selectedDate + 'T00:00:00')
    cur.setDate(cur.getDate() + 1)
    setSelectedDate(cur.toISOString().split('T')[0])
  }

  function handleToday() {
    setSelectedDate(new Date().toISOString().split('T')[0])
  }

  // Status updates
  async function handleStatusUpdate(appointmentId: string, newStatus: 'COMPLETED' | 'NO_SHOW') {
    setActionLoadingId(appointmentId)
    try {
      const updated = await updateAppointmentStatus(appointmentId, newStatus)
      setRoster((prev) =>
        prev.map((item) => (item.appointmentId === appointmentId ? updated : item))
      )
      setToast({
        msg: `Patient marked as ${newStatus === 'COMPLETED' ? 'Completed' : 'No-Show'}.`,
        type: 'success',
      })
    } catch (err: unknown) {
      setToast({
        msg: err instanceof Error ? err.message : 'Failed to update appointment status',
        type: 'error',
      })
    } finally {
      setActionLoadingId(null)
    }
  }

  // Filtered Roster
  const filteredRoster = useMemo(() => {
    return roster.filter((item) => {
      const matchesStatus = filterStatus === 'ALL' || item.status === filterStatus
      const q = searchQuery.toLowerCase().trim()
      const matchesSearch =
        !q ||
        item.patientName.toLowerCase().includes(q) ||
        item.patientContact.includes(q)
      return matchesStatus && matchesSearch
    })
  }, [roster, filterStatus, searchQuery])

  // Metrics
  const metrics = useMemo(() => {
    const total = roster.length
    const booked = roster.filter((r) => r.status === 'BOOKED').length
    const completed = roster.filter((r) => r.status === 'COMPLETED').length
    const noShow = roster.filter((r) => r.status === 'NO_SHOW').length
    const blocked = roster.filter((r) => r.status === 'BLOCKED').length
    return { total, booked, completed, noShow, blocked }
  }, [roster])

  return (
    <div className="page-bg min-h-screen text-slate-100 flex flex-col font-sans">
      {/* ── Navbar ────────────────────────────────────────────────────────── */}
      <header className="border-b border-white/10 bg-[#0a0f1e]/80 backdrop-blur-xl sticky top-0 z-40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-500 to-violet-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <Stethoscope className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-extrabold text-lg tracking-tight gradient-text">Doppsy</span>
                <span className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full bg-cyan-500/15 text-cyan-400 border border-cyan-500/20">
                  Doctor Ops
                </span>
              </div>
              <p className="text-xs text-slate-400">Zero-Wait Clinic Management</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Link
              href="/dashboard"
              className="text-xs text-slate-300 hover:text-white px-3 py-2 rounded-lg border border-white/10 hover:border-cyan-500/40 bg-white/5 transition-all flex items-center gap-1.5"
            >
              <User className="w-3.5 h-3.5 text-cyan-400" />
              <span>Patient Portal</span>
            </Link>
            <button
              onClick={() => {
                logout()
                router.push('/')
              }}
              className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 border border-white/5 transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* ── Main Content ──────────────────────────────────────────────────── */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex-1 w-full space-y-6">
        {/* Doctor Profile Banner */}
        <div className="glass-card p-6 rounded-2xl relative overflow-hidden border border-white/10 bg-gradient-to-r from-cyan-950/30 via-slate-900/60 to-violet-950/30">
          <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div className="flex items-start gap-4">
              <div className="w-16 h-16 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400 font-bold text-xl shrink-0">
                {profile?.fullName ? profile.fullName.replace('Dr. ', '').slice(0, 2).toUpperCase() : 'DR'}
              </div>
              <div>
                <div className="flex items-center gap-2 flex-wrap">
                  <h1 className="text-2xl font-bold text-white tracking-tight">
                    {profile?.fullName || 'Dr. Rohan Mehta'}
                  </h1>
                  <span className="badge-verified flex items-center gap-1 text-xs">
                    <BadgeCheck className="w-3.5 h-3.5" />
                    Verified Provider
                  </span>
                  <span className="badge-premium text-xs">
                    {profile?.subscriptionTier || 'PREMIUM'}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-y-1 gap-x-4 mt-2 text-xs text-slate-300">
                  <span className="flex items-center gap-1 text-cyan-300 font-medium">
                    <Zap className="w-3.5 h-3.5 text-cyan-400" />
                    {profile?.specialty || 'Allopathy'} Specialist
                  </span>
                  <span className="text-slate-500">•</span>
                  <span className="flex items-center gap-1">
                    <Activity className="w-3.5 h-3.5 text-violet-400" />
                    {profile?.clinicName || 'Mehta Multispeciality Clinic'}
                  </span>
                  <span className="text-slate-500">•</span>
                  <span className="flex items-center gap-1 text-slate-400">
                    <MapPin className="w-3.5 h-3.5 text-slate-400" />
                    {profile?.location || 'Bandra West, Mumbai'}
                  </span>
                </div>
              </div>
            </div>

            {/* Date Navigator & Action Controls */}
            <div className="flex flex-wrap items-center gap-2.5">
              <div className="flex items-center bg-white/5 border border-white/10 rounded-xl p-1 text-xs font-semibold">
                <button
                  onClick={handlePrevDay}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                  title="Previous Day"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <div className="px-3 py-1 flex items-center gap-1.5 text-white">
                  <Calendar className="w-3.5 h-3.5 text-cyan-400" />
                  <span>{formatDateHeader(selectedDate)}</span>
                </div>
                <button
                  onClick={handleNextDay}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                  title="Next Day"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>

              <button
                onClick={handleToday}
                className="btn-ghost px-3 py-2 text-xs text-cyan-300 border-cyan-500/30 hover:border-cyan-500"
              >
                Today
              </button>

              <button
                onClick={() => setShowBlockModal(true)}
                className="btn-primary px-4 py-2 text-xs flex items-center gap-1.5 shadow-lg"
              >
                <Lock className="w-3.5 h-3.5" />
                <span>Block Hours</span>
              </button>
            </div>
          </div>
        </div>

        {/* Metrics Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <div className="glass-card p-4 rounded-xl border border-white/10">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-1">
              <span>Total Slots</span>
              <Users className="w-4 h-4 text-slate-400" />
            </div>
            <p className="text-2xl font-extrabold text-white">{metrics.total}</p>
          </div>

          <div className="glass-card p-4 rounded-xl border border-cyan-500/20 bg-cyan-950/10">
            <div className="flex items-center justify-between text-cyan-300 text-xs mb-1">
              <span>Upcoming</span>
              <Clock className="w-4 h-4 text-cyan-400" />
            </div>
            <p className="text-2xl font-extrabold text-cyan-400">{metrics.booked}</p>
          </div>

          <div className="glass-card p-4 rounded-xl border border-emerald-500/20 bg-emerald-950/10">
            <div className="flex items-center justify-between text-emerald-300 text-xs mb-1">
              <span>Completed</span>
              <UserCheck className="w-4 h-4 text-emerald-400" />
            </div>
            <p className="text-2xl font-extrabold text-emerald-400">{metrics.completed}</p>
          </div>

          <div className="glass-card p-4 rounded-xl border border-amber-500/20 bg-amber-950/10">
            <div className="flex items-center justify-between text-amber-300 text-xs mb-1">
              <span>No-Show</span>
              <UserX className="w-4 h-4 text-amber-400" />
            </div>
            <p className="text-2xl font-extrabold text-amber-400">{metrics.noShow}</p>
          </div>

          <div className="glass-card p-4 rounded-xl border border-slate-500/20 col-span-2 sm:col-span-1">
            <div className="flex items-center justify-between text-slate-400 text-xs mb-1">
              <span>Blocked</span>
              <Lock className="w-4 h-4 text-slate-400" />
            </div>
            <p className="text-2xl font-extrabold text-slate-300">{metrics.blocked}</p>
          </div>
        </div>

        {/* Filter and Search Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
            {['ALL', 'BOOKED', 'COMPLETED', 'NO_SHOW', 'BLOCKED'].map((st) => (
              <button
                key={st}
                onClick={() => setFilterStatus(st)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
                  filterStatus === st
                    ? 'bg-cyan-500 text-slate-950 shadow-md shadow-cyan-500/25'
                    : 'bg-white/5 text-slate-400 hover:text-white border border-white/5'
                }`}
              >
                {st === 'ALL' ? 'All Slots' : st.replace('_', '-')}
              </button>
            ))}
          </div>

          <div className="relative w-full sm:w-72">
            <Search className="w-4 h-4 absolute left-3 top-3 text-slate-400" />
            <input
              type="text"
              placeholder="Search patient name or mobile..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="input-field pl-9 py-2 text-xs"
            />
          </div>
        </div>

        {/* Queue / Roster Table */}
        <div className="glass-card rounded-2xl overflow-hidden border border-white/10 shadow-xl">
          <div className="p-4 border-b border-white/10 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h2 className="font-bold text-sm text-white">Daily Queue & Patient Schedule</h2>
              <span className="text-xs text-slate-400">({filteredRoster.length} entries)</span>
            </div>
            <button
              onClick={loadData}
              disabled={loading}
              className="p-1.5 rounded-lg text-slate-400 hover:text-cyan-400 hover:bg-white/5 transition-colors"
              title="Refresh Roster"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
            </button>
          </div>

          {loading ? (
            <div className="p-12 text-center text-slate-400 flex flex-col items-center gap-3">
              <RefreshCw className="w-8 h-8 animate-spin text-cyan-400" />
              <p className="text-sm">Loading clinic roster...</p>
            </div>
          ) : filteredRoster.length === 0 ? (
            <div className="p-12 text-center text-slate-400 space-y-2">
              <Calendar className="w-10 h-10 mx-auto text-slate-600 mb-2" />
              <p className="text-base font-semibold text-slate-300">No appointments scheduled</p>
              <p className="text-xs text-slate-500">
                There are no patient bookings matching your filter on this date.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="bg-white/[0.02] border-b border-white/5 text-slate-400 font-semibold uppercase tracking-wider text-[11px]">
                    <th className="py-3.5 px-4">Time Slot</th>
                    <th className="py-3.5 px-4">Patient Name</th>
                    <th className="py-3.5 px-4">Age</th>
                    <th className="py-3.5 px-4">Contact</th>
                    <th className="py-3.5 px-4">Fee</th>
                    <th className="py-3.5 px-4">Status</th>
                    <th className="py-3.5 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5">
                  {filteredRoster.map((item) => {
                    const isActionBusy = actionLoadingId === item.appointmentId
                    return (
                      <tr
                        key={item.appointmentId}
                        className="hover:bg-white/[0.03] transition-colors group"
                      >
                        {/* Time */}
                        <td className="py-3.5 px-4 font-semibold text-white whitespace-nowrap">
                          <div className="flex items-center gap-2">
                            <Clock className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                            <span>{formatTime(item.appointmentTimestamp)}</span>
                          </div>
                        </td>

                        {/* Patient Name */}
                        <td className="py-3.5 px-4 font-medium text-slate-200">
                          <div className="flex items-center gap-2.5">
                            <div className="w-7 h-7 rounded-full bg-cyan-500/10 text-cyan-300 flex items-center justify-center font-bold text-[11px] shrink-0 border border-cyan-500/20">
                              {item.patientName.slice(0, 1).toUpperCase()}
                            </div>
                            <span className="truncate max-w-[180px]">{item.patientName}</span>
                          </div>
                        </td>

                        {/* Age */}
                        <td className="py-3.5 px-4 text-slate-400 whitespace-nowrap">
                          {item.patientAge ? `${item.patientAge} yrs` : '—'}
                        </td>

                        {/* Contact */}
                        <td className="py-3.5 px-4 text-slate-300 whitespace-nowrap">
                          <div className="flex items-center gap-1.5">
                            <Phone className="w-3 h-3 text-slate-500" />
                            <span>{item.patientContact}</span>
                          </div>
                        </td>

                        {/* Fee */}
                        <td className="py-3.5 px-4 text-slate-300 font-semibold whitespace-nowrap">
                          ₹{item.bookingFee ?? 50}
                        </td>

                        {/* Status */}
                        <td className="py-3.5 px-4 whitespace-nowrap">
                          <span className={statusBadge(item.status)}>
                            {item.status.replace('_', ' ')}
                          </span>
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 px-4 text-right whitespace-nowrap">
                          {item.status === 'BOOKED' ? (
                            <div className="flex items-center justify-end gap-2">
                              <button
                                onClick={() => handleStatusUpdate(item.appointmentId, 'COMPLETED')}
                                disabled={isActionBusy}
                                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 text-emerald-300 font-semibold text-[11px] transition-all disabled:opacity-50"
                                title="Mark patient as attended"
                              >
                                <Check className="w-3 h-3 text-emerald-400" />
                                <span>Completed</span>
                              </button>
                              <button
                                onClick={() => handleStatusUpdate(item.appointmentId, 'NO_SHOW')}
                                disabled={isActionBusy}
                                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-amber-500/15 hover:bg-amber-500/25 border border-amber-500/30 text-amber-300 font-semibold text-[11px] transition-all disabled:opacity-50"
                                title="Mark patient as absent"
                              >
                                <UserX className="w-3 h-3 text-amber-400" />
                                <span>No-Show</span>
                              </button>
                            </div>
                          ) : item.status === 'COMPLETED' ? (
                            <span className="text-[11px] text-emerald-400 font-medium inline-flex items-center gap-1">
                              <CheckCircle2 className="w-3 h-3" /> Attended
                            </span>
                          ) : item.status === 'NO_SHOW' ? (
                            <button
                              onClick={() => handleStatusUpdate(item.appointmentId, 'COMPLETED')}
                              disabled={isActionBusy}
                              className="text-[11px] text-amber-400 hover:text-amber-200 underline font-medium"
                              title="Revert to completed if patient arrived late"
                            >
                              Late Arrival?
                            </button>
                          ) : item.status === 'BLOCKED' ? (
                            <span className="text-[11px] text-slate-500 font-medium flex items-center justify-end gap-1">
                              <Lock className="w-3 h-3" /> Unavailable
                            </span>
                          ) : (
                            <span className="text-[11px] text-slate-500">Open Slot</span>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>

      {/* Block Hours Modal */}
      {showBlockModal && (
        <BlockHoursModal
          selectedDate={selectedDate}
          onClose={() => setShowBlockModal(false)}
          onBlocked={(msg) => {
            setToast({ msg, type: 'success' })
            loadData()
          }}
        />
      )}

      {/* Toast Feedback */}
      {toast && (
        <Toast
          msg={toast.msg}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  )
}
