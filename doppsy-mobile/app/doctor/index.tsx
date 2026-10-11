import React, { useState, useEffect, useCallback, useMemo } from 'react'
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
  RefreshControl,
  Platform,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useRouter } from 'expo-router'
import {
  getDoctorRoster,
  updateAppointmentStatus,
  getStoredUser,
  logout,
} from '../../src/services/api'
import type { RosterItemResponse, AppointmentStatus } from '../../src/types'

type FilterTab = 'ALL' | 'BOOKED' | 'COMPLETED' | 'NO_SHOW'

export default function DoctorDashboardScreen() {
  const router = useRouter()
  const [doctorName, setDoctorName] = useState('Doctor')
  const [appointments, setAppointments] = useState<RosterItemResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [updatingId, setUpdatingId] = useState<string | null>(null)
  const [activeFilter, setActiveFilter] = useState<FilterTab>('ALL')

  // Today's formatted date string (e.g. "2026-10-12")
  const todayIso = useMemo(() => {
    const now = new Date()
    const year = now.getFullYear()
    const month = String(now.getMonth() + 1).padStart(2, '0')
    const day = String(now.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }, [])

  const formattedDisplayDate = useMemo(() => {
    const now = new Date()
    return now.toLocaleDateString('en-US', {
      weekday: 'long',
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  }, [])

  // Load stored doctor user info
  useEffect(() => {
    async function loadUser() {
      const u = await getStoredUser()
      if (u?.fullName) {
        setDoctorName(u.fullName.startsWith('Dr.') ? u.fullName : `Dr. ${u.fullName}`)
      }
    }
    loadUser()
  }, [])

  // Fetch today's roster from backend (GET /api/v1/doctor/roster)
  const fetchRoster = useCallback(async () => {
    try {
      const data = await getDoctorRoster(todayIso)
      setAppointments(data || [])
    } catch (err: unknown) {
      console.warn('Failed to fetch doctor roster', err)
      Alert.alert(
        'Roster Sync Error',
        'Unable to load appointments. Please verify that the backend service is running on port 8080.'
      )
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [todayIso])

  useEffect(() => {
    setLoading(true)
    fetchRoster()
  }, [fetchRoster])

  const onRefresh = useCallback(() => {
    setRefreshing(true)
    fetchRoster()
  }, [fetchRoster])

  // Handle status update (PATCH /api/v1/doctor/appointments/{id}/status)
  const handleUpdateStatus = useCallback(
    (appointment: RosterItemResponse, newStatus: 'COMPLETED' | 'NO_SHOW') => {
      const actionTitle = newStatus === 'COMPLETED' ? 'Mark as Completed' : 'Mark as No-Show'
      const patientLabel = appointment.patientName || 'this patient'
      const confirmMessage =
        newStatus === 'COMPLETED'
          ? `Confirm consultation completion for ${patientLabel}?`
          : `Record ${patientLabel} as a No-Show for this scheduled slot?`

      Alert.alert(actionTitle, confirmMessage, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Confirm',
          style: newStatus === 'COMPLETED' ? 'default' : 'destructive',
          onPress: async () => {
            setUpdatingId(appointment.appointmentId)
            try {
              const updated = await updateAppointmentStatus(
                appointment.appointmentId,
                newStatus
              )
              // Update local state immediately
              setAppointments((prev) =>
                prev.map((item) =>
                  item.appointmentId === appointment.appointmentId
                    ? { ...item, status: updated.status }
                    : item
                )
              )
              Alert.alert(
                'Status Updated',
                `Appointment successfully marked as ${newStatus}.`
              )
            } catch (err: unknown) {
              const errMsg =
                err instanceof Error ? err.message : 'Failed to update status on server.'
              Alert.alert('Update Failed', errMsg)
            } finally {
              setUpdatingId(null)
            }
          },
        },
      ])
    },
    []
  )

  // Sign out confirmation
  const handleSignOut = () => {
    Alert.alert('Sign Out', 'Are you sure you want to exit the doctor portal?', [
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

  // Filter roster items
  const filteredAppointments = useMemo(() => {
    if (activeFilter === 'ALL') return appointments
    return appointments.filter((item) => item.status === activeFilter)
  }, [appointments, activeFilter])

  // Metric counters
  const metrics = useMemo(() => {
    const total = appointments.length
    const booked = appointments.filter((a) => a.status === 'BOOKED').length
    const completed = appointments.filter((a) => a.status === 'COMPLETED').length
    const noShow = appointments.filter((a) => a.status === 'NO_SHOW').length
    return { total, booked, completed, noShow }
  }, [appointments])

  // Helper formatters
  const formatTime = (isoString?: string) => {
    if (!isoString) return '--:--'
    try {
      const d = new Date(isoString)
      return d.toLocaleTimeString('en-US', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      })
    } catch {
      return isoString
    }
  }

  const getStatusBadgeStyle = (status: AppointmentStatus) => {
    switch (status) {
      case 'BOOKED':
        return { bg: 'rgba(6, 182, 212, 0.15)', text: '#06B6D4', label: 'IN QUEUE' }
      case 'COMPLETED':
        return { bg: 'rgba(16, 185, 129, 0.15)', text: '#10B981', label: 'COMPLETED' }
      case 'NO_SHOW':
        return { bg: 'rgba(244, 63, 94, 0.15)', text: '#F43F5E', label: 'NO-SHOW' }
      case 'CANCELLED':
        return { bg: 'rgba(148, 163, 184, 0.15)', text: '#94A3B8', label: 'CANCELLED' }
      case 'BLOCKED':
        return { bg: 'rgba(168, 85, 247, 0.15)', text: '#C084FC', label: 'BLOCKED' }
      default:
        return { bg: 'rgba(100, 116, 139, 0.15)', text: '#94A3B8', label: status }
    }
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      {/* Scrollable Main Container */}
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor="#06B6D4"
            colors={['#06B6D4']}
          />
        }
      >
        {/* Navigation Bar */}
        <View style={styles.navBar}>
          <View>
            <View style={styles.logoRow}>
              <Text style={styles.brandTitle}>Doppsy</Text>
              <View style={styles.roleBadge}>
                <Text style={styles.roleBadgeText}>DOCTOR MD</Text>
              </View>
            </View>
            <Text style={styles.doctorGreeting}>{doctorName}</Text>
          </View>

          <View style={styles.navActions}>
            <TouchableOpacity
              style={styles.switchButton}
              onPress={() => router.push('/dashboard')}
              activeOpacity={0.7}
            >
              <Text style={styles.switchButtonText}>Patient View</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.logoutButton}
              onPress={handleSignOut}
              activeOpacity={0.7}
            >
              <Text style={styles.logoutButtonText}>Sign Out</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Date & Live Status Banner */}
        <View style={styles.bannerContainer}>
          <View style={styles.bannerHeader}>
            <View style={styles.pulseDot} />
            <Text style={styles.bannerStatusText}>LIVE CLINIC ROSTER</Text>
            <Text style={styles.bannerDateText}>•  {formattedDisplayDate}</Text>
          </View>
          <Text style={styles.bannerSubtext}>
            Manage zero-wait patient queue, attendance, and record completion status in real-time.
          </Text>
        </View>

        {/* KPI Metrics Dashboard */}
        <View style={styles.metricsGrid}>
          <View style={[styles.metricCard, styles.metricCardPrimary]}>
            <Text style={styles.metricNumber}>{metrics.total}</Text>
            <Text style={styles.metricLabel}>Total Slots</Text>
          </View>

          <View style={[styles.metricCard, styles.metricCardBooked]}>
            <Text style={[styles.metricNumber, { color: '#06B6D4' }]}>{metrics.booked}</Text>
            <Text style={styles.metricLabel}>In Queue</Text>
          </View>

          <View style={[styles.metricCard, styles.metricCardCompleted]}>
            <Text style={[styles.metricNumber, { color: '#10B981' }]}>{metrics.completed}</Text>
            <Text style={styles.metricLabel}>Completed</Text>
          </View>

          <View style={[styles.metricCard, styles.metricCardNoShow]}>
            <Text style={[styles.metricNumber, { color: '#F43F5E' }]}>{metrics.noShow}</Text>
            <Text style={styles.metricLabel}>No-Show</Text>
          </View>
        </View>

        {/* Filter Tabs */}
        <View style={styles.filterBar}>
          {(['ALL', 'BOOKED', 'COMPLETED', 'NO_SHOW'] as FilterTab[]).map((tab) => {
            const isSelected = activeFilter === tab
            const label =
              tab === 'ALL'
                ? `All (${metrics.total})`
                : tab === 'BOOKED'
                ? `In Queue (${metrics.booked})`
                : tab === 'COMPLETED'
                ? `Completed (${metrics.completed})`
                : `No-Show (${metrics.noShow})`

            return (
              <TouchableOpacity
                key={tab}
                style={[styles.filterChip, isSelected && styles.filterChipActive]}
                onPress={() => setActiveFilter(tab)}
                activeOpacity={0.8}
              >
                <Text style={[styles.filterChipText, isSelected && styles.filterChipTextActive]}>
                  {label}
                </Text>
              </TouchableOpacity>
            )
          })}
        </View>

        {/* Appointments Section Header */}
        <View style={styles.sectionHeaderRow}>
          <Text style={styles.sectionTitle}>Today's Schedule</Text>
          <TouchableOpacity onPress={onRefresh} style={styles.refreshLink} activeOpacity={0.7}>
            <Text style={styles.refreshLinkText}>↻ Refresh</Text>
          </TouchableOpacity>
        </View>

        {/* Schedule Content */}
        {loading ? (
          <View style={styles.loadingBox}>
            <ActivityIndicator size="large" color="#06B6D4" />
            <Text style={styles.loadingText}>Syncing clinic roster with server...</Text>
          </View>
        ) : filteredAppointments.length === 0 ? (
          <View style={styles.emptyCard}>
            <View style={styles.emptyIconCircle}>
              <Text style={styles.emptyIcon}>🩺</Text>
            </View>
            <Text style={styles.emptyTitle}>No Appointments Found</Text>
            <Text style={styles.emptySubtitle}>
              {activeFilter === 'ALL'
                ? 'Your roster has no scheduled appointments for today.'
                : `No appointments match the "${activeFilter}" filter.`}
            </Text>
            <TouchableOpacity style={styles.emptyActionBtn} onPress={onRefresh} activeOpacity={0.8}>
              <Text style={styles.emptyActionBtnText}>Reload Roster</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View style={styles.appointmentList}>
            {filteredAppointments.map((item, index) => {
              const isUpdating = updatingId === item.appointmentId
              const badge = getStatusBadgeStyle(item.status)
              const patientInitials = (item.patientName || 'Patient')
                .split(' ')
                .map((n) => n[0])
                .join('')
                .slice(0, 2)
                .toUpperCase()

              return (
                <View key={item.appointmentId || `slot-${index}`} style={styles.appointmentCard}>
                  {/* Card Header: Slot Time & Status */}
                  <View style={styles.cardTopRow}>
                    <View style={styles.timeTag}>
                      <Text style={styles.timeClockIcon}>⏱</Text>
                      <Text style={styles.timeTagText}>
                        {formatTime(item.appointmentTimestamp)}
                      </Text>
                      <View style={styles.slotPill}>
                        <Text style={styles.slotPillText}>Zero-Wait</Text>
                      </View>
                    </View>

                    <View style={[styles.statusBadge, { backgroundColor: badge.bg }]}>
                      <Text style={[styles.statusBadgeText, { color: badge.text }]}>
                        {badge.label}
                      </Text>
                    </View>
                  </View>

                  {/* Patient Info Row */}
                  <View style={styles.patientRow}>
                    <View style={styles.avatarCircle}>
                      <Text style={styles.avatarText}>{patientInitials}</Text>
                    </View>

                    <View style={styles.patientDetails}>
                      <Text style={styles.patientName}>
                        {item.patientName || 'Walk-in / Unassigned'}
                      </Text>

                      <View style={styles.patientSubRow}>
                        {item.patientAge ? (
                          <View style={styles.metaBadge}>
                            <Text style={styles.metaBadgeText}>{item.patientAge} yrs</Text>
                          </View>
                        ) : null}

                        {item.patientContact ? (
                          <View style={styles.metaBadge}>
                            <Text style={styles.metaBadgeText}>📞 {item.patientContact}</Text>
                          </View>
                        ) : null}

                        <View style={styles.metaBadge}>
                          <Text style={styles.metaBadgeText}>
                            Fee: ₹{item.bookingFee ?? 500}
                          </Text>
                        </View>
                      </View>
                    </View>
                  </View>

                  {/* Action Buttons for Scheduled/Booked Appointments */}
                  {item.status === 'BOOKED' ? (
                    <View style={styles.actionsRow}>
                      {isUpdating ? (
                        <View style={styles.actionUpdatingBox}>
                          <ActivityIndicator size="small" color="#06B6D4" />
                          <Text style={styles.actionUpdatingText}>Updating status...</Text>
                        </View>
                      ) : (
                        <>
                          {/* Mark as Completed Button */}
                          <TouchableOpacity
                            style={[styles.touchableBtn, styles.btnComplete]}
                            onPress={() => handleUpdateStatus(item, 'COMPLETED')}
                            activeOpacity={0.8}
                          >
                            <Text style={styles.btnIcon}>✓</Text>
                            <Text style={styles.btnCompleteText}>Mark Completed</Text>
                          </TouchableOpacity>

                          {/* Mark as No-Show Button */}
                          <TouchableOpacity
                            style={[styles.touchableBtn, styles.btnNoShow]}
                            onPress={() => handleUpdateStatus(item, 'NO_SHOW')}
                            activeOpacity={0.8}
                          >
                            <Text style={styles.btnIcon}>✕</Text>
                            <Text style={styles.btnNoShowText}>Mark No-Show</Text>
                          </TouchableOpacity>
                        </>
                      )}
                    </View>
                  ) : item.status === 'COMPLETED' ? (
                    <View style={styles.resolvedInfoBox}>
                      <Text style={styles.resolvedCompletedText}>
                        ✓ Consultation completed successfully. Records saved.
                      </Text>
                    </View>
                  ) : item.status === 'NO_SHOW' ? (
                    <View style={styles.resolvedInfoBox}>
                      <Text style={styles.resolvedNoShowText}>
                        ⚠ Patient marked as no-show. Slot concluded.
                      </Text>
                    </View>
                  ) : null}
                </View>
              )
            })}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#0A0F1E',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 40,
  },

  // Navbar
  navBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.08)',
    marginBottom: 16,
  },
  logoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  brandTitle: {
    fontSize: 22,
    fontWeight: '800',
    color: '#F8FAFC',
    letterSpacing: -0.5,
  },
  roleBadge: {
    backgroundColor: 'rgba(6, 182, 212, 0.2)',
    borderWidth: 1,
    borderColor: '#06B6D4',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 6,
  },
  roleBadgeText: {
    color: '#06B6D4',
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  doctorGreeting: {
    color: '#94A3B8',
    fontSize: 13,
    marginTop: 2,
  },
  navActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  switchButton: {
    backgroundColor: '#1E293B',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  switchButtonText: {
    color: '#CBD5E1',
    fontSize: 12,
    fontWeight: '600',
  },
  logoutButton: {
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    borderWidth: 1,
    borderColor: 'rgba(239, 68, 68, 0.3)',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 8,
  },
  logoutButtonText: {
    color: '#EF4444',
    fontSize: 12,
    fontWeight: '600',
  },

  // Live Banner
  bannerContainer: {
    backgroundColor: '#131D31',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
    marginBottom: 16,
  },
  bannerHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 6,
  },
  pulseDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#10B981',
    marginRight: 8,
  },
  bannerStatusText: {
    color: '#10B981',
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  bannerDateText: {
    color: '#94A3B8',
    fontSize: 12,
    fontWeight: '500',
  },
  bannerSubtext: {
    color: '#94A3B8',
    fontSize: 12,
    lineHeight: 18,
  },

  // Metrics Grid
  metricsGrid: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  metricCard: {
    flex: 1,
    backgroundColor: '#131D31',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  metricCardPrimary: {
    borderColor: '#334155',
  },
  metricCardBooked: {
    borderColor: 'rgba(6, 182, 212, 0.3)',
  },
  metricCardCompleted: {
    borderColor: 'rgba(16, 185, 129, 0.3)',
  },
  metricCardNoShow: {
    borderColor: 'rgba(244, 63, 94, 0.3)',
  },
  metricNumber: {
    fontSize: 20,
    fontWeight: '800',
    color: '#F8FAFC',
    marginBottom: 2,
  },
  metricLabel: {
    fontSize: 10,
    fontWeight: '600',
    color: '#94A3B8',
    textTransform: 'uppercase',
  },

  // Filter Bar
  filterBar: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 16,
  },
  filterChip: {
    backgroundColor: '#131D31',
    paddingVertical: 7,
    paddingHorizontal: 12,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  filterChipActive: {
    backgroundColor: 'rgba(6, 182, 212, 0.18)',
    borderColor: '#06B6D4',
  },
  filterChipText: {
    color: '#94A3B8',
    fontSize: 12,
    fontWeight: '600',
  },
  filterChipTextActive: {
    color: '#06B6D4',
    fontWeight: '700',
  },

  // Section Header
  sectionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    color: '#F8FAFC',
    fontSize: 16,
    fontWeight: '700',
  },
  refreshLink: {
    paddingVertical: 4,
    paddingHorizontal: 8,
  },
  refreshLinkText: {
    color: '#06B6D4',
    fontSize: 12,
    fontWeight: '600',
  },

  // Appointment List & Cards
  appointmentList: {
    gap: 12,
  },
  appointmentCard: {
    backgroundColor: '#131D31',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  cardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
    paddingBottom: 10,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.05)',
  },
  timeTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  timeClockIcon: {
    fontSize: 12,
  },
  timeTagText: {
    color: '#F8FAFC',
    fontSize: 14,
    fontWeight: '700',
  },
  slotPill: {
    backgroundColor: 'rgba(234, 179, 8, 0.15)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  slotPillText: {
    color: '#EAB308',
    fontSize: 9,
    fontWeight: '700',
  },
  statusBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  statusBadgeText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 0.5,
  },

  // Patient Row
  patientRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginBottom: 14,
  },
  avatarCircle: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(6, 182, 212, 0.15)',
    borderWidth: 1,
    borderColor: '#06B6D4',
    justifyContent: 'center',
    alignItems: 'center',
  },
  avatarText: {
    color: '#06B6D4',
    fontSize: 15,
    fontWeight: '800',
  },
  patientDetails: {
    flex: 1,
  },
  patientName: {
    color: '#F8FAFC',
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 4,
  },
  patientSubRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  metaBadge: {
    backgroundColor: '#1E293B',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  metaBadgeText: {
    color: '#94A3B8',
    fontSize: 11,
    fontWeight: '500',
  },

  // Action Buttons
  actionsRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  touchableBtn: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 10,
    borderRadius: 10,
    gap: 6,
  },
  btnIcon: {
    fontSize: 13,
    color: '#FFFFFF',
    fontWeight: '800',
  },
  btnComplete: {
    backgroundColor: '#059669',
  },
  btnCompleteText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
  btnNoShow: {
    backgroundColor: '#E11D48',
  },
  btnNoShowText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
  actionUpdatingBox: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 10,
    backgroundColor: '#1E293B',
    borderRadius: 10,
  },
  actionUpdatingText: {
    color: '#94A3B8',
    fontSize: 12,
  },

  // Resolved Info Box
  resolvedInfoBox: {
    backgroundColor: '#0F172A',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  resolvedCompletedText: {
    color: '#10B981',
    fontSize: 12,
    fontWeight: '500',
  },
  resolvedNoShowText: {
    color: '#F43F5E',
    fontSize: 12,
    fontWeight: '500',
  },

  // Loading State
  loadingBox: {
    paddingVertical: 50,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingText: {
    color: '#94A3B8',
    fontSize: 13,
  },

  // Empty State
  emptyCard: {
    backgroundColor: '#131D31',
    borderRadius: 14,
    padding: 30,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  emptyIconCircle: {
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#1E293B',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  emptyIcon: {
    fontSize: 28,
  },
  emptyTitle: {
    color: '#F8FAFC',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 6,
  },
  emptySubtitle: {
    color: '#94A3B8',
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 16,
  },
  emptyActionBtn: {
    backgroundColor: '#06B6D4',
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 8,
  },
  emptyActionBtnText: {
    color: '#0A0F1E',
    fontSize: 13,
    fontWeight: '700',
  },
})
