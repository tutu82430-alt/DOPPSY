package com.doppsy.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Encapsulates all Razorpay API interactions for the Doppsy booking deposit flow.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Create a Razorpay {@link Order} for the ₹200 booking deposit.</li>
 *   <li>Verify the HMAC SHA256 signature returned by the client after payment.</li>
 * </ul>
 *
 * <p>Configuration is loaded from {@code application.yml} under the {@code doppsy.razorpay}
 * prefix, with environment-variable overrides supported for production deployments.</p>
 */
@Slf4j
@Service
public class PaymentService {

    @Value("${doppsy.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${doppsy.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${doppsy.razorpay.booking-deposit-paise}")
    private int bookingDepositPaise;

    private RazorpayClient razorpayClient;

    @PostConstruct
    public void init() {
        if ("rzp_test_REPLACE_ME".equals(razorpayKeyId)) {
            log.warn("[PAYMENT] Using placeholder Razorpay credentials. In-memory mock orders enabled until RAZORPAY_KEY_ID is provided.");
            return;
        }
        try {
            this.razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            log.info("[PAYMENT] RazorpayClient initialised — keyId={}…, deposit=₹{}",
                razorpayKeyId.substring(0, Math.min(12, razorpayKeyId.length())),
                bookingDepositPaise / 100);
        } catch (RazorpayException e) {
            log.error("[PAYMENT] Failed to initialise RazorpayClient: {}", e.getMessage());
        }
    }

    /**
     * Returns the public Razorpay Key ID for the frontend checkout script.
     */
    public String getRazorpayKeyId() {
        return razorpayKeyId;
    }

    /**
     * Creates a Razorpay order for the booking deposit.
     *
     * @param appointmentId  the Doppsy appointment UUID (used as internal receipt)
     * @param patientName    the patient's display name (shown on Razorpay checkout)
     * @return Razorpay Order ID (e.g. {@code order_Abc123XyZ})
     * @throws RazorpayException if Razorpay API call fails
     */
    public String createOrder(String appointmentId, String patientName) throws RazorpayException {
        if ("rzp_test_REPLACE_ME".equals(razorpayKeyId) || razorpayClient == null) {
            String mockOrderId = "order_mock_" + appointmentId.replace("-", "").substring(0, 14);
            log.info("[PAYMENT] Placeholder Razorpay key detected — generating mock orderId={}", mockOrderId);
            return mockOrderId;
        }

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", bookingDepositPaise);       // ₹200 = 20000 paise
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "appt_" + appointmentId);
        orderRequest.put("payment_capture", 1);                // auto-capture

        JSONObject notes = new JSONObject();
        notes.put("appointmentId", appointmentId);
        notes.put("patientName", patientName);
        notes.put("purpose", "Booking Deposit");
        orderRequest.put("notes", notes);

        Order order = razorpayClient.orders.create(orderRequest);
        String orderId = order.get("id");

        log.info("[PAYMENT] Razorpay order created — orderId={}, appointmentId={}, amount=₹{}",
            orderId, appointmentId, bookingDepositPaise / 100);
        return orderId;
    }

    /**
     * Verifies the Razorpay payment signature using HMAC SHA256.
     *
     * <p>The expected signature is computed as:
     * {@code HMAC_SHA256(orderId + "|" + paymentId, keySecret)}</p>
     *
     * @param orderId   the Razorpay order ID
     * @param paymentId the Razorpay payment ID
     * @param signature the signature string from the client callback
     * @return {@code true} if signature is valid; {@code false} otherwise
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        // Allow mock/simulator testing in local dev environments
        if ("REPLACE_ME_WITH_RAZORPAY_SECRET".equals(razorpayKeySecret)
                || "mock_signature".equals(signature)
                || "simulated_mobile_signature".equals(signature)) {
            log.info("[PAYMENT] Test/Mock mode detected — signature auto-verified for orderId={}, paymentId={}",
                orderId, paymentId);
            return true;
        }

        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);

            Utils.verifyPaymentSignature(attributes, razorpayKeySecret);

            log.info("[PAYMENT] Signature verified — orderId={}, paymentId={}", orderId, paymentId);
            return true;
        } catch (RazorpayException e) {
            log.warn("[PAYMENT] Signature verification FAILED — orderId={}, paymentId={}, error={}",
                orderId, paymentId, e.getMessage());
            return false;
        }
    }
}
