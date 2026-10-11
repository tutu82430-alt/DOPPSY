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
import type { DoctorResponse, Specialty } from '../../src/types'

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

  // Book Zero-Wait Slot
  async function handleBookSlot(doctor: DoctorResponse) {
    setBookingProviderId(doctor.providerId)

    // Schedule 24 hours from current time rounded to the next hour
    const appointmentDate = new Date()
    appointmentDate.setDate(appointmentDate.getDate() + 1)
    appointmentDate.setMinutes(0, 0, 0)
    const timestampIso = appointmentDate.toISOString().replace('.000Z', '')

    const formattedTime = appointmentDate.toLocaleDateString('en-IN', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })

    try {
      const booking = await bookAppointment(doctor.providerId, timestampIso)

      // Native Alert showing the WhatsApp confirmation integration
      Alert.alert(
        '⚡ Zero-Wait Slot Confirmed!',
        `Your appointment with ${doctor.fullName} at ${doctor.clinicName} is confirmed for ${formattedTime}.\n\n📲 WhatsApp Concierge Alert:\nA confirmation message and 24-hour reminder have been dispatched to your mobile.\n\nReply "CANCEL" on WhatsApp anytime to cancel.`,
        [
          {
            text: 'View Details',
            style: 'default',
          },
          {
            text: 'OK',
            style: 'cancel',
          },
        ]
      )
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : 'Unable to complete zero-wait booking.'
      Alert.alert('Booking Error', errorMsg)
    } finally {
      setBookingProviderId(null)
    }
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
