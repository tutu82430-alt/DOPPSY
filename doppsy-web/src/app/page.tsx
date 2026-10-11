'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { sendOtp, login } from '@/services/api'
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  ChevronRight,
  Loader2,
  Phone,
  Shield,
  Stethoscope,
  Heart,
} from 'lucide-react'

// ─── Step type ───────────────────────────────────────────────────────────────
type Step = 'phone' | 'otp' | 'success'

// ─── Feature cards data ──────────────────────────────────────────────────────
const features = [
  {
    icon: Stethoscope,
    title: 'Allopathy & Ayurveda',
    desc: 'Verified doctors across 9 specialties',
    color: 'text-cyan-400',
    bg: 'bg-cyan-500/10',
  },
  {
    icon: Heart,
    title: 'Veterinary Care',
    desc: 'Premium care for your beloved pets',
    color: 'text-rose-400',
    bg: 'bg-rose-500/10',
  },
  {
    icon: Activity,
    title: 'Zero Wait Time',
    desc: 'Instant slot booking, no queues ever',
    color: 'text-violet-400',
    bg: 'bg-violet-500/10',
  },
  {
    icon: Shield,
    title: 'Verified Providers',
    desc: 'Every provider credential-checked',
    color: 'text-emerald-400',
    bg: 'bg-emerald-500/10',
  },
]

// ─── OTP digit display ────────────────────────────────────────────────────────
function OtpDisplay({ value }: { value: string }) {
  const digits = value.padEnd(6, ' ').split('')
  return (
    <div className="flex gap-2 justify-center my-4">
      {digits.map((d, i) => (
        <div
          key={i}
          className={`w-10 h-12 rounded-lg border text-center flex items-center justify-center text-lg font-bold transition-all duration-200
            ${d.trim() ? 'border-cyan-500/60 bg-cyan-500/10 text-cyan-300' : 'border-white/10 bg-white/5 text-transparent'}`}
        >
          {d.trim() || '·'}
        </div>
      ))}
    </div>
  )
}

// ─── Page ────────────────────────────────────────────────────────────────────
export default function LoginPage() {
  const router = useRouter()
  const [step, setStep] = useState<Step>('phone')
  const [mobile, setMobile] = useState('')
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  // ── Send OTP ───────────────────────────────────────────────────────────────
  async function handleSendOtp(e: React.FormEvent) {
    e.preventDefault()
    if (mobile.length < 7) { setError('Enter a valid mobile number'); return }
    setError('')
    setLoading(true)
    try {
      await sendOtp(mobile)
      setStep('otp')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to send OTP')
    } finally {
      setLoading(false)
    }
  }

  // ── Verify OTP ─────────────────────────────────────────────────────────────
  async function handleVerifyOtp(e: React.FormEvent) {
    e.preventDefault()
    if (otp.length < 4) { setError('Enter the OTP you received'); return }
    setError('')
    setLoading(true)
    try {
      await login(mobile, otp)
      setStep('success')
      setTimeout(() => router.push('/dashboard'), 1200)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Invalid OTP. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page-bg min-h-screen flex">

      {/* ── Left Panel — Branding ── */}
      <div className="hidden lg:flex flex-col justify-between w-1/2 p-16 relative overflow-hidden">
        {/* Background glow orbs */}
        <div className="absolute -top-32 -left-32 w-96 h-96 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 right-0 w-80 h-80 bg-violet-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Logo */}
        <div className="flex items-center gap-3 relative z-10">
          <div className="w-10 h-10 rounded-xl bg-brand-gradient flex items-center justify-center">
            <Activity className="w-5 h-5 text-white" />
          </div>
          <span className="text-xl font-bold text-white tracking-tight">Doppsy</span>
        </div>

        {/* Hero copy */}
        <div className="relative z-10 space-y-8">
          <div className="space-y-4">
            <h1 className="text-5xl font-extrabold leading-tight">
              Healthcare,{' '}
              <span className="gradient-text">reimagined</span>
              <br />for everyone.
            </h1>
            <p className="text-lg text-slate-400 leading-relaxed max-w-md">
              Book verified doctors, veterinarians, and dieticians
              instantly — no waiting rooms, no paperwork, just care.
            </p>
          </div>

          {/* Feature grid */}
          <div className="grid grid-cols-2 gap-4">
            {features.map((f) => (
              <div key={f.title} className="glass-card p-4 space-y-2">
                <div className={`w-9 h-9 rounded-lg ${f.bg} flex items-center justify-center`}>
                  <f.icon className={`w-4 h-4 ${f.color}`} />
                </div>
                <p className="text-sm font-semibold text-white">{f.title}</p>
                <p className="text-xs text-slate-500">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom stat bar */}
        <div className="flex gap-8 relative z-10">
          {[['10K+', 'Patients'], ['500+', 'Providers'], ['₹0', 'Hidden Fees']].map(([val, label]) => (
            <div key={label}>
              <p className="text-2xl font-bold gradient-text">{val}</p>
              <p className="text-xs text-slate-500">{label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* ── Right Panel — Auth Card ── */}
      <div className="flex flex-col items-center justify-center flex-1 px-6 py-12">

        {/* Mobile logo */}
        <div className="flex lg:hidden items-center gap-2 mb-10">
          <div className="w-9 h-9 rounded-xl bg-brand-gradient flex items-center justify-center">
            <Activity className="w-4 h-4 text-white" />
          </div>
          <span className="text-lg font-bold text-white">Doppsy</span>
        </div>

        <div className="w-full max-w-md">
          <div className="glass-card p-8 animate-slide-up" style={{ boxShadow: '0 25px 50px rgba(0,0,0,0.4)' }}>

            {/* ── Success state ── */}
            {step === 'success' && (
              <div className="flex flex-col items-center gap-4 py-8 animate-fade-in">
                <div className="w-16 h-16 rounded-full bg-emerald-500/15 flex items-center justify-center animate-pulse-glow">
                  <CheckCircle2 className="w-8 h-8 text-emerald-400" />
                </div>
                <h2 className="text-xl font-bold text-white">Welcome to Doppsy!</h2>
                <p className="text-sm text-slate-400">Redirecting to your dashboard…</p>
                <Loader2 className="w-5 h-5 text-cyan-400 animate-spin" />
              </div>
            )}

            {/* ── Phone step ── */}
            {step === 'phone' && (
              <>
                <div className="mb-6">
                  <h2 className="text-2xl font-bold text-white mb-1">Sign in to Doppsy</h2>
                  <p className="text-sm text-slate-400">Enter your mobile number to receive an OTP</p>
                </div>

                <form onSubmit={handleSendOtp} className="space-y-4">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-slate-400 uppercase tracking-wider">
                      Mobile Number
                    </label>
                    <div className="relative">
                      <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500">
                        <Phone className="w-4 h-4" />
                      </span>
                      <input
                        id="mobile-input"
                        type="tel"
                        value={mobile}
                        onChange={(e) => { setMobile(e.target.value.replace(/\D/g, '')); setError('') }}
                        placeholder="9876543210"
                        maxLength={15}
                        className="input-field pl-10"
                        autoFocus
                        required
                      />
                    </div>
                  </div>

                  {error && (
                    <p className="text-xs text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">
                      {error}
                    </p>
                  )}

                  <button
                    id="send-otp-btn"
                    type="submit"
                    disabled={loading || mobile.length < 7}
                    className="btn-primary w-full py-3.5"
                  >
                    {loading ? (
                      <><Loader2 className="w-4 h-4 animate-spin" /> Sending OTP…</>
                    ) : (
                      <>Get OTP <ArrowRight className="w-4 h-4" /></>
                    )}
                  </button>
                </form>

                <p className="text-xs text-slate-500 text-center mt-4">
                  By continuing you agree to our{' '}
                  <span className="text-cyan-400 cursor-pointer hover:underline">Terms of Service</span>
                </p>
              </>
            )}

            {/* ── OTP step ── */}
            {step === 'otp' && (
              <>
                <div className="mb-6">
                  <button
                    onClick={() => { setStep('phone'); setOtp(''); setError('') }}
                    className="text-xs text-slate-500 hover:text-cyan-400 transition-colors flex items-center gap-1 mb-4"
                  >
                    ← Change number
                  </button>
                  <h2 className="text-2xl font-bold text-white mb-1">Verify your number</h2>
                  <p className="text-sm text-slate-400">
                    OTP sent to <span className="text-cyan-400 font-medium">+91 {mobile}</span>
                  </p>
                  <p className="text-xs text-amber-400/70 mt-1">
                    Dev hint: use OTP <strong>123456</strong>
                  </p>
                </div>

                <OtpDisplay value={otp} />

                <form onSubmit={handleVerifyOtp} className="space-y-4">
                  <input
                    id="otp-input"
                    type="text"
                    inputMode="numeric"
                    value={otp}
                    onChange={(e) => { setOtp(e.target.value.replace(/\D/g, '').slice(0, 6)); setError('') }}
                    placeholder="Enter 6-digit OTP"
                    className="input-field text-center text-xl tracking-widest"
                    maxLength={6}
                    autoFocus
                    required
                  />

                  {error && (
                    <p className="text-xs text-red-400 bg-red-500/10 border border-red-500/20 rounded-lg px-3 py-2">
                      {error}
                    </p>
                  )}

                  <button
                    id="verify-otp-btn"
                    type="submit"
                    disabled={loading || otp.length < 4}
                    className="btn-primary w-full py-3.5"
                  >
                    {loading ? (
                      <><Loader2 className="w-4 h-4 animate-spin" /> Verifying…</>
                    ) : (
                      <>Verify &amp; Continue <ChevronRight className="w-4 h-4" /></>
                    )}
                  </button>
                </form>
              </>
            )}
          </div>

          <p className="text-center text-xs text-slate-600 mt-6">
            © 2026 Doppsy Healthcare Pvt. Ltd.
          </p>
        </div>
      </div>
    </div>
  )
}
