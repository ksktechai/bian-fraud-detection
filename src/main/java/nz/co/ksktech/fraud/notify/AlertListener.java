package nz.co.ksktech.fraud.notify;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Logs fraud {@link AlertEvent}s emitted by the {@code notify} operation. This is the integration
 * seam: swap or supplement this observer to push alerts to Kafka, email, or a case-management
 * system without touching the REST resource.
 */
@ApplicationScoped
public class AlertListener {

  /**
   * Handles a fraud alert event by logging it.
   *
   * @param event the alert event
   */
  public void onAlert(@Observes AlertEvent event) {
    Log.warnf(
        "FRAUD-ALERT ref=%s txn=%s decision=%s riskScore=%s :: %s",
        event.referenceId(),
        event.transactionId(),
        event.decision(),
        event.riskScore(),
        event.message());
  }
}
