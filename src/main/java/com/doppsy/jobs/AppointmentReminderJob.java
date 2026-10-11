package com.doppsy.jobs;

import com.doppsy.entity.Appointment;
import com.doppsy.entity.Provider;
import com.doppsy.entity.User;
import com.doppsy.repository.AppointmentRepository;
import com.doppsy.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Background cron job that runs at the top of every hour to dispatch 24-hour
 * WhatsApp reminders for upcoming appointments.
 *
 * <p>Reminder template:
 * <em>"Reminder: Your appointment with [Doctor Name] is tomorrow at [Time]. Reply CANCEL if you cannot make it."</em>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderJob {

    private final AppointmentRepository appointmentRepository;
    private final WhatsAppService whatsappService;

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("hh:mm a");

    /**
     * Executes at the top of every hour (e.g. 09:00:00, 10:00:00).
     * Finds all BOOKED appointments scheduled between 24 and 25 hours from now
     * and sends automated WhatsApp reminders.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void send24HourAppointmentReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.plusHours(24);
        LocalDateTime windowEnd = now.plusHours(25);

        log.info("[REMINDER JOB] Starting 24h reminder scan for window {} to {}", windowStart, windowEnd);

        List<Appointment> upcomingAppointments =
            appointmentRepository.findBookedAppointmentsInWindow(windowStart, windowEnd);

        if (upcomingAppointments.isEmpty()) {
            log.info("[REMINDER JOB] No booked appointments scheduled in the 24h window.");
            return;
        }

        log.info("[REMINDER JOB] Found {} appointment(s) needing 24h reminders.", upcomingAppointments.size());

        int sentCount = 0;
        for (Appointment appt : upcomingAppointments) {
            User patient = appt.getUser();
            Provider doctor = appt.getProvider();

            if (patient == null || patient.getMobileNumber() == null || patient.getMobileNumber().isBlank()) {
                log.warn("[REMINDER JOB] Skipping appointment {} — missing patient mobile number", appt.getAppointmentId());
                continue;
            }

            String doctorName = (doctor != null) ? doctor.getFullName() : "your healthcare provider";
            String appointmentTime = appt.getAppointmentTimestamp().format(TIME_FMT);

            String message = String.format(
                "Reminder: Your appointment with %s is tomorrow at %s. Reply CANCEL if you cannot make it.",
                doctorName, appointmentTime
            );

            boolean sent = whatsappService.sendMessage(patient.getMobileNumber(), message);
            if (sent) {
                sentCount++;
                log.info("[REMINDER JOB] Sent reminder to {} for appointment with {} at {}",
                    patient.getMobileNumber(), doctorName, appointmentTime);
            }
        }

        log.info("[REMINDER JOB] Finished dispatching 24h reminders. Total sent: {}/{}",
            sentCount, upcomingAppointments.size());
    }

    /**
     * Manual or on-demand execution helper for testing and administration.
     * Scans any BOOKED appointments within the specified forward hour window.
     *
     * @param forwardHours hours into the future to scan (e.g. 24)
     * @return number of reminders dispatched
     */
    @Transactional(readOnly = true)
    public int dispatchRemindersForWindow(int forwardHours) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.plusHours(forwardHours);
        LocalDateTime windowEnd = windowStart.plusHours(1);

        log.info("[REMINDER JOB MANUAL] Scanning window {} to {}", windowStart, windowEnd);

        List<Appointment> appointments =
            appointmentRepository.findBookedAppointmentsInWindow(windowStart, windowEnd);

        int count = 0;
        for (Appointment appt : appointments) {
            if (appt.getUser() != null && appt.getUser().getMobileNumber() != null) {
                String doctorName = (appt.getProvider() != null) ? appt.getProvider().getFullName() : "your doctor";
                String timeStr = appt.getAppointmentTimestamp().format(TIME_FMT);
                String msg = String.format(
                    "Reminder: Your appointment with %s is tomorrow at %s. Reply CANCEL if you cannot make it.",
                    doctorName, timeStr
                );
                whatsappService.sendMessage(appt.getUser().getMobileNumber(), msg);
                count++;
            }
        }
        return count;
    }
}
