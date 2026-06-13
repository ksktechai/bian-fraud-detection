package nz.co.ksktech.fraud.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import nz.co.ksktech.fraud.domain.FraudEvaluation;

/** Request/response payloads for {@link FraudDetectionResource}. */
public final class FraudDtos {

  private FraudDtos() {}

  /**
   * Payload to initiate a fraud evaluation control record (PaySim transaction shape).
   *
   * @param transactionId the originating transaction id
   * @param amount the transaction amount
   * @param type the PaySim transaction type (PAYMENT, TRANSFER, CASH_OUT, ...)
   * @param nameOrig the originating account
   * @param nameDest the destination account
   * @param oldbalanceOrg the originator balance before the transaction
   * @param newbalanceOrig the originator balance after the transaction
   */
  public record InitiateRequest(
      String transactionId,
      BigDecimal amount,
      String type,
      String nameOrig,
      String nameDest,
      BigDecimal oldbalanceOrg,
      BigDecimal newbalanceOrig) {}

  /**
   * Analyst disposition override applied via the {@code update} operation.
   *
   * @param decision the overriding decision (CLEAR | REVIEW | BLOCK)
   * @param caseNarrative the analyst's case note
   * @param riskScore optional overriding risk score (0-100); null keeps the AI score
   */
  public record UpdateRequest(String decision, String caseNarrative, Integer riskScore) {}

  /** Optional message override for the {@code notify} operation. */
  public record NotifyRequest(String message) {}

  /**
   * Full view of a control record returned by the BIAN operations.
   *
   * @param referenceId the BIAN control-record id
   * @param transactionId the originating transaction id
   * @param amount the transaction amount
   * @param type the PaySim transaction type
   * @param nameOrig the originating account
   * @param nameDest the destination account
   * @param oldbalanceOrg the originator balance before the transaction
   * @param newbalanceOrig the originator balance after the transaction
   * @param riskScore the AI/analyst risk score (0-100)
   * @param decision the current decision (CLEAR | REVIEW | BLOCK)
   * @param aiReasoning the AI rationale
   * @param caseNarrative the analyst-facing case narrative
   * @param status the lifecycle status (INITIATED | EVALUATED | DISPOSED)
   * @param createdAt creation timestamp
   * @param updatedAt last-update timestamp
   */
  public record EvaluationResponse(
      UUID referenceId,
      String transactionId,
      BigDecimal amount,
      String type,
      String nameOrig,
      String nameDest,
      BigDecimal oldbalanceOrg,
      BigDecimal newbalanceOrig,
      Integer riskScore,
      String decision,
      String aiReasoning,
      String caseNarrative,
      String status,
      Instant createdAt,
      Instant updatedAt) {

    /**
     * Maps an entity to its response view.
     *
     * @param e the control-record entity
     * @return the response DTO
     */
    public static EvaluationResponse from(FraudEvaluation e) {
      return new EvaluationResponse(
          e.referenceId,
          e.transactionId,
          e.amount,
          e.type,
          e.nameOrig,
          e.nameDest,
          e.oldbalanceOrg,
          e.newbalanceOrig,
          e.riskScore,
          e.decision,
          e.aiReasoning,
          e.caseNarrative,
          e.status,
          e.createdAt,
          e.updatedAt);
    }
  }
}
