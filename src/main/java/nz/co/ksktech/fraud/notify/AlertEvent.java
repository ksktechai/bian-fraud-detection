package nz.co.ksktech.fraud.notify;

import java.util.UUID;

/**
 * Fired by the BIAN {@code notify} operation to broadcast a fraud alert. Carrying the control-record
 * reference, decision and score lets downstream observers (logging today, a message broker or case
 * system tomorrow) react without coupling to the REST layer.
 *
 * @param referenceId the BIAN control-record id
 * @param transactionId the originating transaction id
 * @param decision the current decision (CLEAR | REVIEW | BLOCK)
 * @param riskScore the current risk score
 * @param message a human-readable alert message
 */
public record AlertEvent(
    UUID referenceId, String transactionId, String decision, Integer riskScore, String message) {}
