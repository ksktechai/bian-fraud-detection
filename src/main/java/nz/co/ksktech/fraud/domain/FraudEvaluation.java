package nz.co.ksktech.fraud.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * BIAN Fraud Detection control record. One instance tracks a single transaction through the
 * service-domain lifecycle: INITIATED (control record created) -> EVALUATED (AI triage applied) ->
 * DISPOSED (analyst override recorded). {@code referenceId} is the BIAN control-record id surfaced
 * on every endpoint.
 */
@Entity
@Table(name = "fraud_evaluation")
public class FraudEvaluation extends PanacheEntityBase {

  @Id
  @Column(name = "reference_id")
  public UUID referenceId;

  @Column(name = "transaction_id")
  public String transactionId;

  public BigDecimal amount;

  public String type;

  @Column(name = "name_orig")
  public String nameOrig;

  @Column(name = "name_dest")
  public String nameDest;

  @Column(name = "oldbalance_org")
  public BigDecimal oldbalanceOrg;

  @Column(name = "newbalance_orig")
  public BigDecimal newbalanceOrig;

  @Column(name = "risk_score")
  public Integer riskScore;

  /** CLEAR | REVIEW | BLOCK. */
  public String decision;

  @Column(name = "ai_reasoning", columnDefinition = "text")
  public String aiReasoning;

  @Column(name = "case_narrative", columnDefinition = "text")
  public String caseNarrative;

  /** INITIATED | EVALUATED | DISPOSED. */
  public String status;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt = Instant.now();

  /**
   * Finds a control record by its BIAN reference id.
   *
   * @param referenceId the control-record id
   * @return an Optional containing the record if found, otherwise empty
   */
  public static Optional<FraudEvaluation> findByReference(UUID referenceId) {
    return Optional.ofNullable(findById(referenceId));
  }
}
