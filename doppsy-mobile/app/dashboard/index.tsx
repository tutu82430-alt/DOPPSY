import React, { useState, useEffect, useCallback } from 'react'
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
  RefreshControl,
  ScrollView,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useRouter } from 'expo-router'
import { getDoctors, bookAppointment, logout, getStoredUser } from '../../src/services/api'
import type { DoctorResponse, BookingResponse, Specialty } from '../../src/types'
import RazorpayPaymentModal from '../../src/components/RazorpayPaymentModal'

const SPECIALTIES = [
  'ALL',
  'Allopathy',
  'Ayurveda',
  'Veterinary',
  'Dietician',
  'Dentistry',
]

export default function PatientDashboardScreen() {
  const router = useRouter()
  const [userName, setUserName] = useState('Patient')
  const [doctors, setDoctors] = useState<DoctorResponse[]>([])
  const [selectedSpecialty, setSelectedSpecialty] = useState<string>('ALL')
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [bookingProviderId, setBookingProviderId] = useState<string | null>(null)
  const [pendingBooking, setPendingBooking] = useState<BookingResponse | null>(null)
  const [showPaymentModal, setShowPaymentModal] = useState(false)

  // Load user details
  useEffect(() => {
    async function loadUser() {
      const u = await getStoredUser()
      if (u?.fullName) {
        setUserName(u.fullName)
      }
    }
    loadUser()
  }, [])

  // Fetch doctors
  const fetchDoctors = useCallback(async () => {
    try {
      const res = await getDoctors(selectedSpecialty)
      setDoctors(res)
    } catch (err: unknown) {
      console.warn('Failed to fetch doctors', err)
      Alert.alert(
        'Connection Error',
        'Unable to load doctors list. Please ensure your Spring Boot backend is running on port 8080.'
      )
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [selectedSpecialty])

  useEffect(() => {
    setLoading(true)
    fetchDoctors()
  }, [fetchDoctors])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    fetchDoctors()
  }, [fetchDoctors])

  // ── Navigate to booking confirmation screen ────────────────────────────────
  function navigateToConfirmation(confirmedBooking: BookingResponse) {
    router.push({
      pathname: '/confirmation',
      params: {
        appointmentId: confirmedBooking.appointmentId,
        providerName: confirmedBooking.providerName,
        clinicName: confirmedBooking.clinicName,
        appointmentTimestamp: confirmedBooking.appointmentTimestamp,
        bookingFee: (confirmedBooking.bookingFee || 200).toString(),
        razorpayOrderId: confirmedBooking.razorpayOrderId || '',
      },
    })
  }

  // ── Book Zero-Wait Slot with Razorpay Deposit ────────────────────────────────
  async function handleBookSlot(selectedDoctor: DoctorResponse) {
    // Guard clause: prevent concurrent duplicate clicks
    if (bookingProviderId) {
      return
    }

    setBookingProviderId(selectedDoctor.providerId)

    // Schedule 24 hours from current time rounded to the next hour
    const scheduledSlotDate = new Date()
    scheduledSlotDate.setDate(scheduledSlotDate.getDate() + 1)
    scheduledSlotDate.setMinutes(0, 0, 0)
    const slotTimestampIso = scheduledSlotDate.toISOString().replace('.000Z', '')

    try {
      // Step 1: Initiate booking on backend — returns status PENDING_PAYMENT + Razorpay Order ID
      const pendingBookingTxn = await bookAppointment(selectedDoctor.providerId, slotTimestampIso)

      // Guard clause: fallback if zero-fee or immediate confirmation
      if (!pendingBookingTxn.razorpayOrderId) {
        navigateToConfirmation(pendingBookingTxn)
        return
      }

      // Step 2: Configure native Razorpay checkout options
      // Note: Razorpay expects the amount in base currency (paise), so multiply by 100
      const depositAmountInPaise = pendingBookingTxn.bookingFee
        ? Math.round(pendingBookingTxn.bookingFee * 100)
        : 20000 // default ₹200 booking deposit

      const razorpayCheckoutOptions = {
        description: `₹200 Booking Deposit - Dr. ${selectedDoctor.fullName}`,
        image: 'https://doppsy.com/brand/logo.png',
        currency: 'INR',
        key: pendingBookingTxn.razorpayKeyId || 'rzp_test_REPLACE_ME',
        amount: depositAmountInPaise,
        name: 'Doppsy Healthcare Concierge',
        order_id: pendingBookingTxn.razorpayOrderId,
        prefill: {
          name: userName || 'Patient',
          contact: '9999999999',
          email: 'patient@doppsy.com',
        },
        theme: { color: '#06B6D4' },
      }

      // Step 3: Launch Native Razorpay Checkout with try/catch dismissal handling
      let nativeRazorpayModule: { open: (options: Record<string, unknown>) => Promise<{ razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }> } | null = null
      try {
        const rzp = require('react-native-razorpay')
        nativeRazorpayModule = rzp.default || rzp
      } catch {
        // Native module not linked (e.g. running inside Expo Go client)
        nativeRazorpayModule = null
      }

      if (nativeRazorpayModule && typeof nativeRazorpayModule.open === 'function') {
        try {
          const paymentAuthorizationResponse = await nativeRazorpayModule.open(razorpayCheckoutOptions)

          // Step 4: Verify cryptographic HMAC SHA256 signature on backend
          const confirmedBooking = await verifyPayment(
            paymentAuthorizationResponse.razorpay_order_id || pendingBookingTxn.razorpayOrderId,
            paymentAuthorizationResponse.razorpay_payment_id,
            paymentAuthorizationResponse.razorpay_signature
          )

          navigateToConfirmation(confirmedBooking)
          return
        } catch (checkoutError: any) {
          // Graceful handling for user cancellation or back button
          if (checkoutError?.code === 0 || checkoutError?.description?.toLowerCase().includes('cancel') || checkoutError?.description?.toLowerCase().includes('dismiss')) {
            console.warn('User dismissed the payment modal')
            return
          }
          console.warn('Native Razorpay error, falling back to sheet:', checkoutError)
        }
      }

      // Fallback: Open in-app payment sheet (for Expo Go / simulators)
      setPendingBooking(pendingBookingTxn)
      setShowPaymentModal(true)

    } catch (bookingInitiationError: unknown) {
      const errorMessage = bookingInitiationError instanceof Error
        ? bookingInitiationError.message
        : 'Unable to initiate zero-wait booking.'
      Alert.alert('Booking Error', errorMessage)
    } finally {
      setBookingProviderId(null)
    }
  }

  // Handle Successful Razorpay Verification from in-app modal
  function handlePaymentSuccess(verifiedBooking: BookingResponse) {
    setShowPaymentModal(false)
    setPendingBooking(null)
    navigateToConfirmation(verifiedBooking)
  }

  // Handle Logout
  async function handleLogout() {
    Alert.alert('Sign Out', 'Are you sure you want to sign out?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Sign Out',
        style: 'destructive',
        onPress: async () => {
          await logout()
          router.replace('/')
        },
      },
    ])
  }

  // Render Doctor Card
  const renderDoctorItem = ({ item }: { item: DoctorResponse }) => {
    const isBooking = bookingProviderId === item.providerId
    const initials = item.fullName.replace('Dr. ', '').slice(0, 2).toUpperCase()

    return (
      <View style={styles.doctorCard}>
        {/* Card Header */}
        <View style={styles.cardHeader}>
          <View style={styles.avatarCircle}>
            <Text style={styles.avatarText}>{initials}</Text>
          </View>

          <View style={styles.headerInfo}>
            <View style={styles.nameRow}>
              <Text style={styles.doctorName}>{item.fullName}</Text>
              {item.isVerified && (
                <View style={styles.verifiedTag}>
                  <Text style={styles.verifiedTagText}>✓</Text>
                </View>
              )}
            </View>
            <Text style={styles.clinicName}>{item.clinicName || 'Doppsy Concierge Clinic'}</Text>
          </View>

          <View style={styles.tierBadge}>
            <Text style={styles.tierBadgeText}>{item.subscriptionTier}</Text>
          </View>
        </View>

        {/* Badges / Specialty */}
        <View style={styles.tagsRow}>
          <View style={styles.specialtyChip}>
            <Text style={styles.specialtyChipText}>{item.specialty}</Text>
          </View>
          <View style={styles.locationChip}>
            <Text style={styles.locationChipText}>📍 {item.location || 'Pan-City'}</Text>
          </View>
          <View style={styles.zeroWaitBadge}>
            <Text style={styles.zeroWaitBadgeText}>⚡ Zero-Wait</Text>
          </View>
        </View>

        {/* Booking CTA */}
        <TouchableOpacity
          style={[styles.bookButton, isBooking && styles.buttonDisabled]}
          onPress={() => handleBookSlot(item)}
          disabled={isBooking}
          activeOpacity={0.8}
        >
          {isBooking ? (
            <ActivityIndicator color="#0A0F1E" size="small" />
          ) : (
            <Text style={styles.bookButtonText}>⚡ Book Zero-Wait Slot</Text>
          )}
        </TouchableOpacity>
      </View>
    )
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      {/* App Bar */}
      <View style={styles.navbar}>
        <View>
          <View style={styles.logoRow}>
            <Text style={styles.logoTitle}>Doppsy</Text>
            <View style={styles.patientBadge}>
              <Text style={styles.patientBadgeText}>PATIENT</Text>
            </View>
          </View>
          <Text style={styles.greetingText}>Welcome back, {userName.split(' ')[0]}</Text>
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <TouchableOpacity
            style={styles.doctorSwitchButton}
            onPress={() => router.push('/doctor')}
            activeOpacity={0.7}
          >
            <Text style={styles.doctorSwitchButtonText}>Doctor View</Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.logoutButton} onPress={handleLogout} activeOpacity={0.7}>
            <Text style={styles.logoutButtonText}>Sign Out</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Specialty Filter Scroll */}
      <View style={styles.filterSection}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterScroll}
        >
          {SPECIALTIES.map((spec) => {
            const isSelected = selectedSpecialty === spec
            return (
              <TouchableOpacity
                key={spec}
                style={[styles.filterChip, isSelected && styles.filterChipActive]}
                onPress={() => setSelectedSpecialty(spec)}
                activeOpacity={0.8}
              >
                <Text style={[styles.filterChipText, isSelected && styles.filterChipTextActive]}>
                  {spec === 'ALL' ? 'All Specialties' : spec}
                </Text>
              </TouchableOpacity>
            )
          })}
        </ScrollView>
      </View>

      {/* Doctor List */}
      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#06B6D4" />
          <Text style={styles.loadingText}>Fetching available specialists...</Text>
        </View>
      ) : (
        <FlatList
          data={doctors}
          keyExtractor={(item) => item.providerId}
          renderItem={renderDoctorItem}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onRefresh}
              tintColor="#06B6D4"
              colors={['#06B6D4']}
            />
          }
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyIcon}>🩺</Text>
              <Text style={styles.emptyTitle}>No Specialists Found</Text>
              <Text style={styles.emptySubtitle}>
                No verified providers matched your filter. Try selecting 'All Specialties'.
              </Text>
            </View>
          }
        />
      )}

      {/* Razorpay Deposit Payment Sheet Modal */}
      <RazorpayPaymentModal
        visible={showPaymentModal}
        booking={pendingBooking}
        patientName={userName}
        onClose={() => setShowPaymentModal(false)}
        onSuccess={handlePaymentSuccess}
      />
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0A0F1E',
  },
  navbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
    backgroundColor: '#0A0F1E',
  },
  logoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  logoTitle: {
    fontSize: 22,
    fontWeight: '900',
    color: '#06B6D4',
    letterSpacing: -0.5,
  },
  patientBadge: {
    backgroundColor: 'rgba(6, 182, 212, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(6, 182, 212, 0.3)',
    borderRadius: 8,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  patientBadgeText: {
    fontSize: 9,
    fontWeight: '800',
    color: '#06B6D4',
  },
  greetingText: {
    fontSize: 13,
    color: '#94A3B8',
    marginTop: 2,
    fontWeight: '500',
  },
  logoutButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#334155',
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
  },
  logoutButtonText: {
    fontSize: 12,
    color: '#94A3B8',
    fontWeight: '600',
  },
  doctorSwitchButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: '#06B6D4',
    backgroundColor: 'rgba(6, 182, 212, 0.12)',
  },
  doctorSwitchButtonText: {
    fontSize: 12,
    color: '#06B6D4',
    fontWeight: '700',
  },
  filterSection: {
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.04)',
  },
  filterScroll: {
    paddingHorizontal: 20,
    gap: 8,
  },
  filterChip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderWidth: 1,
    borderColor: '#1E293B',
    marginRight: 8,
  },
  filterChipActive: {
    backgroundColor: '#06B6D4',
    borderColor: '#06B6D4',
  },
  filterChipText: {
    fontSize: 12,
    color: '#94A3B8',
    fontWeight: '600',
  },
  filterChipTextActive: {
    color: '#0A0F1E',
    fontWeight: '700',
  },
  listContent: {
    padding: 20,
    paddingBottom: 40,
  },
  doctorCard: {
    backgroundColor: '#131B2E',
    borderRadius: 20,
    padding: 18,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 6,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  avatarCircle: {
    width: 48,
    height: 48,
    borderRadius: 14,
    backgroundColor: 'rgba(6, 182, 212, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(6, 182, 212, 0.3)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  avatarText: {
    color: '#06B6D4',
    fontSize: 16,
    fontWeight: '800',
  },
  headerInfo: {
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  doctorName: {
    fontSize: 17,
    fontWeight: '700',
    color: '#F8FAFC',
  },
  verifiedTag: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#10B981',
    alignItems: 'center',
    justifyContent: 'center',
  },
  verifiedTagText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '900',
  },
  clinicName: {
    fontSize: 13,
    color: '#94A3B8',
    marginTop: 2,
  },
  tierBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 8,
    backgroundColor: 'rgba(245, 158, 11, 0.12)',
    borderWidth: 1,
    borderColor: 'rgba(245, 158, 11, 0.3)',
  },
  tierBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    color: '#FBBF24',
  },
  tagsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 16,
  },
  specialtyChip: {
    backgroundColor: 'rgba(6, 182, 212, 0.08)',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderWidth: 1,
    borderColor: 'rgba(6, 182, 212, 0.2)',
  },
  specialtyChipText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#38BDF8',
  },
  locationChip: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  locationChipText: {
    fontSize: 11,
    color: '#94A3B8',
  },
  zeroWaitBadge: {
    backgroundColor: 'rgba(139, 92, 246, 0.12)',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderWidth: 1,
    borderColor: 'rgba(139, 92, 246, 0.3)',
  },
  zeroWaitBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#A78BFA',
  },
  bookButton: {
    height: 46,
    borderRadius: 12,
    backgroundColor: '#06B6D4',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#06B6D4',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 5,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  bookButtonText: {
    color: '#0A0F1E',
    fontSize: 14,
    fontWeight: '800',
    letterSpacing: 0.2,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingText: {
    color: '#94A3B8',
    fontSize: 14,
    fontWeight: '500',
  },
  emptyContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 64,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 12,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#F8FAFC',
    marginBottom: 6,
  },
  emptySubtitle: {
    fontSize: 13,
    color: '#94A3B8',
    textAlign: 'center',
    paddingHorizontal: 32,
    lineHeight: 18,
  },
})
