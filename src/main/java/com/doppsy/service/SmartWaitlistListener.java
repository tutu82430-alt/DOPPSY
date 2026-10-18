package com.doppsy.service;

import com.doppsy.config.RabbitMQConfig;
import com.doppsy.event.SlotCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Enterprise RabbitMQ consumer driving the Smart Waitlist Engine.
 *
 * <p>Listens for {@link SlotCancelledEvent} messages broadcast to the
 * {@code doppsy.slot.cancelled.queue}. Upon receiving a cancellation, it delegates
 * to {@link WaitlistService} to query the top 3 FIFO waitlisted patients for that
 * doctor's consultation date and sends WhatsApp opening invitations.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartWaitlistListener {

    private final WaitlistService waitlistService;

    /**
     * Consumes SlotCancelledEvent from RabbitMQ queue.
     *
     * @param cancelledEvent the deserialized event payload
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_SLOT_CANCELLED)
    public void onSlotCancelled(SlotCancelledEvent cancelledEvent) {
        // Guard clause: ignore null or malformed payloads
        if (cancelledEvent == null) {
            log.warn("[RABBITMQ WAITLIST CONSUMER] Received null SlotCancelledEvent; discarding message");
            return;
        }

        log.info("[RABBITMQ WAITLIST CONSUMER] Ingested SlotCancelledEvent: AppointmentID: {}, ProviderID: {}, Date: {}",
            cancelledEvent.getAppointmentId(),
            cancelledEvent.getProviderId(),
            cancelledEvent.getSlotDate());

        try {
            waitlistService.processSlotCancellation(cancelledEvent);
            log.info("[RABBITMQ WAITLIST CONSUMER] Successfully processed cancellation for AppointmentID: {}",
                cancelledEvent.getAppointmentId());
        } catch (Exception processingEx) {
            log.error("[RABBITMQ WAITLIST ERROR] Failed to process SlotCancelledEvent for AppointmentID: {}: {}",
                cancelledEvent.getAppointmentId(), processingEx.getMessage(), processingEx);
            // In enterprise production, messages can be routed to a dead-letter exchange (DLX)
            // or re-thrown based on retry policy.
        }
    }
}
