import React, { useState, useEffect } from 'react'
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useRouter } from 'expo-router'
import AsyncStorage from '@react-native-async-storage/async-storage'
import { sendOtp, loginWithOtp } from '../src/services/api'

export default function LoginScreen() {
  const router = useRouter()
  const [mobileNumber, setMobileNumber] = useState('')
  const [otp, setOtp] = useState('')
  const [step, setStep] = useState<'PHONE' | 'OTP'>('PHONE')
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  // Check if session already exists
  useEffect(() => {
    async function checkExistingAuth() {
      try {
        const token = await AsyncStorage.getItem('doppsy_token')
        if (token) {
          router.replace('/dashboard')
        }
      } catch (e) {
        console.warn('Auth check error', e)
      }
    }
    checkExistingAuth()
  }, [router])

  // Step 1: Send OTP
  async function handleSendOtp() {
    if (mobileNumber.trim().length < 8) {
      setErrorMessage('Please enter a valid mobile number (min 8 digits)')
      return
    }
    setErrorMessage('')
    setLoading(true)

    try {
      await sendOtp(mobileNumber.trim())
      setStep('OTP')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Unable to send OTP. Is the backend running?'
      setErrorMessage(msg)
    } finally {
      setLoading(false)
    }
  }

  // Step 2: Verify OTP
  async function handleVerifyOtp() {
    if (otp.trim().length < 4) {
      setErrorMessage('Please enter the 6-digit OTP code')
      return
    }
    setErrorMessage('')
    setLoading(true)

    try {
      await loginWithOtp(mobileNumber.trim(), otp.trim())
      router.replace('/dashboard')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Invalid OTP. Try 123456 in dev mode.'
      setErrorMessage(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardContainer}
      >
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled">
          {/* Header Brand */}
          <View style={styles.brandContainer}>
            <View style={styles.logoBadge}>
              <Text style={styles.logoIcon}>⚡</Text>
            </View>
            <Text style={styles.brandTitle}>Doppsy</Text>
            <Text style={styles.brandTagline}>Zero-Wait Healthcare Concierge</Text>
          </View>

          {/* Login Card */}
          <View style={styles.card}>
            <Text style={styles.cardTitle}>
              {step === 'PHONE' ? 'Sign In / Register' : 'Enter Verification Code'}
            </Text>
            <Text style={styles.cardSubtitle}>
              {step === 'PHONE'
                ? 'Enter your mobile number to receive an instant OTP login'
                : `We sent a 6-digit code to ${mobileNumber}`}
            </Text>

            {/* Error Message */}
            {errorMessage ? (
              <View style={styles.errorBox}>
                <Text style={styles.errorText}>⚠️ {errorMessage}</Text>
              </View>
            ) : null}

            {step === 'PHONE' ? (
              <>
                <Text style={styles.inputLabel}>MOBILE NUMBER</Text>
                <View style={styles.phoneInputRow}>
                  <View style={styles.countryCodeBadge}>
                    <Text style={styles.countryCodeText}>+91</Text>
                  </View>
                  <TextInput
                    style={styles.phoneInput}
                    placeholder="9876543210"
                    placeholderTextColor="#64748B"
                    keyboardType="phone-pad"
                    value={mobileNumber}
                    onChangeText={(val) => {
                      setMobileNumber(val)
                      setErrorMessage('')
                    }}
                    autoFocus
                  />
                </View>

                <TouchableOpacity
                  style={[styles.primaryButton, loading && styles.buttonDisabled]}
                  onPress={handleSendOtp}
                  disabled={loading}
                  activeOpacity={0.8}
                >
                  {loading ? (
                    <ActivityIndicator color="#FFFFFF" size="small" />
                  ) : (
                    <Text style={styles.primaryButtonText}>Get OTP Code →</Text>
                  )}
                </TouchableOpacity>
              </>
            ) : (
              <>
                <Text style={styles.inputLabel}>6-DIGIT OTP</Text>
                <TextInput
                  style={styles.otpInput}
                  placeholder="123456"
                  placeholderTextColor="#64748B"
                  keyboardType="number-pad"
                  maxLength={6}
                  value={otp}
                  onChangeText={(val) => {
                    setOtp(val)
                    setErrorMessage('')
                  }}
                  autoFocus
                />

                <View style={styles.hintBox}>
                  <Text style={styles.hintText}>💡 Dev Hint: Use OTP "123456" to verify</Text>
                </View>

                <TouchableOpacity
                  style={[styles.primaryButton, loading && styles.buttonDisabled]}
                  onPress={handleVerifyOtp}
                  disabled={loading}
                  activeOpacity={0.8}
                >
                  {loading ? (
                    <ActivityIndicator color="#FFFFFF" size="small" />
                  ) : (
                    <Text style={styles.primaryButtonText}>Verify &amp; Continue →</Text>
                  )}
                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.backButton}
                  onPress={() => {
                    setStep('PHONE')
                    setOtp('')
                    setErrorMessage('')
                  }}
                >
                  <Text style={styles.backButtonText}>← Change Mobile Number</Text>
                </TouchableOpacity>
              </>
            )}
          </View>

          {/* Concierge Features List */}
          <View style={styles.featureRow}>
            <View style={styles.featureItem}>
              <Text style={styles.featureEmoji}>🩺</Text>
              <Text style={styles.featureText}>Verified Doctors</Text>
            </View>
            <View style={styles.featureItem}>
              <Text style={styles.featureEmoji}>⏱️</Text>
              <Text style={styles.featureText}>Zero Wait Times</Text>
            </View>
            <View style={styles.featureItem}>
              <Text style={styles.featureEmoji}>💬</Text>
              <Text style={styles.featureText}>WhatsApp Alerts</Text>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0A0F1E',
  },
  keyboardContainer: {
    flex: 1,
  },
  scrollContent: {
    flexGrow: 1,
    paddingHorizontal: 24,
    paddingTop: 36,
    paddingBottom: 24,
    justifyContent: 'center',
  },
  brandContainer: {
    alignItems: 'center',
    marginBottom: 32,
  },
  logoBadge: {
    width: 64,
    height: 64,
    borderRadius: 20,
    backgroundColor: 'rgba(6, 182, 212, 0.15)',
    borderWidth: 1.5,
    borderColor: '#06B6D4',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
    shadowColor: '#06B6D4',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 8,
  },
  logoIcon: {
    fontSize: 32,
  },
  brandTitle: {
    fontSize: 32,
    fontWeight: '800',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  brandTagline: {
    fontSize: 14,
    color: '#94A3B8',
    marginTop: 6,
    fontWeight: '500',
  },
  card: {
    backgroundColor: '#131B2E',
    borderRadius: 24,
    padding: 24,
    borderWidth: 1,
    borderColor: '#1E293B',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.4,
    shadowRadius: 16,
    elevation: 10,
  },
  cardTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 6,
  },
  cardSubtitle: {
    fontSize: 13,
    color: '#94A3B8',
    lineHeight: 18,
    marginBottom: 20,
  },
  errorBox: {
    backgroundColor: 'rgba(239, 68, 68, 0.12)',
    borderColor: 'rgba(239, 68, 68, 0.3)',
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    marginBottom: 16,
  },
  errorText: {
    color: '#FCA5A5',
    fontSize: 12,
    fontWeight: '500',
  },
  inputLabel: {
    fontSize: 11,
    fontWeight: '700',
    color: '#64748B',
    letterSpacing: 1,
    marginBottom: 8,
  },
  phoneInputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  countryCodeBadge: {
    height: 52,
    paddingHorizontal: 14,
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderWidth: 1,
    borderColor: '#1E293B',
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  countryCodeText: {
    color: '#06B6D4',
    fontWeight: '700',
    fontSize: 15,
  },
  phoneInput: {
    flex: 1,
    height: 52,
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderWidth: 1,
    borderColor: '#1E293B',
    borderRadius: 14,
    paddingHorizontal: 16,
    color: '#F8FAFC',
    fontSize: 16,
    fontWeight: '600',
  },
  otpInput: {
    height: 54,
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderWidth: 1,
    borderColor: '#06B6D4',
    borderRadius: 14,
    paddingHorizontal: 16,
    color: '#06B6D4',
    fontSize: 22,
    fontWeight: '800',
    letterSpacing: 10,
    textAlign: 'center',
    marginBottom: 12,
  },
  hintBox: {
    backgroundColor: 'rgba(6, 182, 212, 0.08)',
    borderRadius: 10,
    padding: 8,
    marginBottom: 20,
  },
  hintText: {
    color: '#38BDF8',
    fontSize: 12,
    textAlign: 'center',
    fontWeight: '500',
  },
  primaryButton: {
    height: 52,
    backgroundColor: '#06B6D4',
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#06B6D4',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 8,
    elevation: 6,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  primaryButtonText: {
    color: '#0A0F1E',
    fontSize: 15,
    fontWeight: '700',
  },
  backButton: {
    marginTop: 16,
    alignItems: 'center',
  },
  backButtonText: {
    color: '#94A3B8',
    fontSize: 13,
    fontWeight: '500',
  },
  featureRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: 36,
    paddingTop: 24,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.06)',
  },
  featureItem: {
    alignItems: 'center',
  },
  featureEmoji: {
    fontSize: 22,
    marginBottom: 6,
  },
  featureText: {
    color: '#94A3B8',
    fontSize: 12,
    fontWeight: '500',
  },
})
