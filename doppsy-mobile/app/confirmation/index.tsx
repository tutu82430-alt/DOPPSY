import React from 'react'
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useRouter, useLocalSearchParams } from 'expo-router'

export default function ConfirmationScreen() {
  const router = useRouter()
  const params = useLocalSearchParams<{
    appointmentId?: string
    providerName?: string
    clinicName?: string
    appointmentTimestamp?: string
    bookingFee?: string
    razorpayOrderId?: string
  }>()

  const providerName = params.providerName || 'Healthcare Specialist'
  const clinicName = params.clinicName || 'Doppsy Clinic Partner'
  const bookingFee = params.bookingFee || '200'
  const razorpayOrderId = params.razorpayOrderId || 'rzp_order_completed'
  const appointmentId = params.appointmentId || 'APT-' + Math.random().toString(36).substring(2, 8).toUpperCase()

  const formattedTime = params.appointmentTimestamp
    ? new Date(params.appointmentTimestamp).toLocaleString('en-IN', {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : 'Tomorrow, Priority Slot'

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Success Icon */}
        <View style={styles.iconContainer}>
          <View style={styles.iconOuterRing}>
            <View style={styles.iconInnerCircle}>
              <Text style={styles.checkIcon}>✓</Text>
            </View>
          </View>
        </View>

        {/* Header */}
        <Text style={styles.title}>Zero-Wait Slot Confirmed!</Text>
        <Text style={styles.subtitle}>
          Your priority appointment and ₹{bookingFee} booking deposit have been verified via Razorpay.
        </Text>

        {/* Appointment Card */}
        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardHeaderTag}>VERIFIED BOOKING</Text>
            <Text style={styles.statusBadge}>BOOKED</Text>
          </View>

          <View style={styles.cardBody}>
            <View style={styles.row}>
              <Text style={styles.rowLabel}>Doctor</Text>
              <Text style={styles.rowValue}>{providerName}</Text>
            </View>

            <View style={styles.row}>
              <Text style={styles.rowLabel}>Clinic</Text>
              <Text style={styles.rowValue}>{clinicName}</Text>
            </View>

            <View style={styles.row}>
              <Text style={styles.rowLabel}>Date &amp; Time</Text>
              <Text style={[styles.rowValue, styles.highlightValue]}>{formattedTime}</Text>
            </View>

            <View style={styles.row}>
              <Text style={styles.rowLabel}>Deposit Paid</Text>
              <Text style={[styles.rowValue, styles.feeValue]}>₹{bookingFee} (Razorpay)</Text>
            </View>

            <View style={styles.divider} />

            <View style={styles.row}>
              <Text style={styles.rowLabel}>Appointment ID</Text>
              <Text style={[styles.rowValue, styles.monoValue]}>{appointmentId.slice(0, 18)}</Text>
            </View>

            <View style={styles.row}>
              <Text style={styles.rowLabel}>Razorpay Order</Text>
              <Text style={[styles.rowValue, styles.monoValue]}>{razorpayOrderId}</Text>
            </View>
          </View>
        </View>

        {/* WhatsApp Notification Card */}
        <View style={styles.whatsappCard}>
          <Text style={styles.whatsappIcon}>📲</Text>
          <View style={styles.whatsappTextContainer}>
            <Text style={styles.whatsappTitle}>WhatsApp Concierge Dispatched</Text>
            <Text style={styles.whatsappSubtitle}>
              A confirmation pass and a 24-hour reminder have been sent to your WhatsApp number. You can reply &quot;CANCEL&quot; anytime to release your slot.
            </Text>
          </View>
        </View>

        {/* Buttons */}
        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={() => router.replace('/dashboard')}
          >
            <Text style={styles.primaryButtonText}>Return to Dashboard</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0F1E',
  },
  scrollContent: {
    padding: 24,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100%',
  },
  iconContainer: {
    marginBottom: 20,
    marginTop: 10,
  },
  iconOuterRing: {
    width: 90,
    height: 90,
    borderRadius: 45,
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    borderWidth: 2,
    borderColor: 'rgba(16, 185, 129, 0.4)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconInnerCircle: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: '#10B981',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#10B981',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.5,
    shadowRadius: 16,
    elevation: 8,
  },
  checkIcon: {
    color: '#0A0F1E',
    fontSize: 32,
    fontWeight: '900',
  },
  title: {
    fontSize: 24,
    fontWeight: '800',
    color: '#FFFFFF',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 14,
    color: '#94A3B8',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
    paddingHorizontal: 12,
  },
  card: {
    width: '100%',
    backgroundColor: '#111827',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    overflow: 'hidden',
    marginBottom: 20,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 18,
    paddingVertical: 14,
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.06)',
  },
  cardHeaderTag: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    color: '#06B6D4',
  },
  statusBadge: {
    fontSize: 11,
    fontWeight: '700',
    color: '#10B981',
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  cardBody: {
    padding: 18,
    gap: 12,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  rowLabel: {
    fontSize: 13,
    color: '#64748B',
  },
  rowValue: {
    fontSize: 14,
    fontWeight: '600',
    color: '#F1F5F9',
  },
  highlightValue: {
    color: '#38BDF8',
  },
  feeValue: {
    color: '#34D399',
    fontWeight: '700',
  },
  monoValue: {
    fontFamily: 'Courier',
    fontSize: 11,
    color: '#94A3B8',
  },
  divider: {
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    marginVertical: 4,
  },
  whatsappCard: {
    width: '100%',
    flexDirection: 'row',
    backgroundColor: 'rgba(16, 185, 129, 0.08)',
    borderWidth: 1,
    borderColor: 'rgba(16, 185, 129, 0.25)',
    borderRadius: 16,
    padding: 16,
    marginBottom: 28,
  },
  whatsappIcon: {
    fontSize: 26,
    marginRight: 14,
    marginTop: 2,
  },
  whatsappTextContainer: {
    flex: 1,
  },
  whatsappTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#34D399',
    marginBottom: 4,
  },
  whatsappSubtitle: {
    fontSize: 12,
    color: '#CBD5E1',
    lineHeight: 18,
  },
  buttonContainer: {
    width: '100%',
  },
  primaryButton: {
    width: '100%',
    backgroundColor: '#06B6D4',
    borderRadius: 16,
    paddingVertical: 16,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#06B6D4',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 10,
    elevation: 4,
  },
  primaryButtonText: {
    color: '#0A0F1E',
    fontSize: 16,
    fontWeight: '700',
  },
})
