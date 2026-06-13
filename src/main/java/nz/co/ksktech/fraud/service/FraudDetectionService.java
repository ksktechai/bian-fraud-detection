package nz.co.ksktech.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import nz.co.ksktech.fraud.ai.FraudTriageService;
import nz.co.ksktech.fraud.ai.FraudVerdict;
import nz.co.ksktech.fraud.api.FraudDtos.InitiateRequest;
import nz.co.ksktech.fraud.api.FraudDtos.UpdateRequest;
import nz.co.ksktech.fraud.domain.FraudEvaluation;
import nz.co.ksktech.fraud.notify.AlertEvent;

/**
 * Orchestrates the BIAN Fraud Detection lifecycle: initiate -> evaluate -> update -> notify. Holds
 * all transactional boundaries so {@link nz.co.ksktech.fraud.api.FraudDetectionResource} stays a
 * thin HTTP adapter.
 */
@ApplicationScoped
public class FraudDetectionService {

  private final FraudTriageService triageService;
  private final ObjectMapper objectMapper;
  private final Event<AlertEvent> alertEvent;

  /**
   * Constructor.
   *
   * @param triageService the AI triage service
   * @param objectMapper the JSON mapper
   * @param alertEvent the CDI alert-event emitter
   */
  public FraudDetectionService(
      FraudTriageService triageService, ObjectMapper objectMapper, Event<AlertEvent> alertEvent) {
    this.triageService = triageService;
    this.objectMapper = objectMapper;
    this.alertEvent = alertEvent;
  }

  /**
   * Creates a new control record in INITIATED status.
   *
   * @param request the initiation payload
   * @return the persisted control record
   */
  @Transactional
  public FraudEvaluation initiate(InitiateRequest request) {
    FraudEvaluation e = new FraudEvaluation();
    e.referenceId = UUID.randomUUID();
    e.transactionId = request.transactionId();
    e.amount = request.amount();
    e.type = request.type();
    e.nameOrig = request.nameOrig();
    e.nameDest = request.nameDest();
    e.oldbalanceOrg = request.oldbalanceOrg();
    e.newbalanceOrig = request.newbalanceOrig();
    e.status = "INITIATED";
    e.createdAt = Instant.now();
    e.updatedAt = e.createdAt;
    e.persist();
    return e;
  }

  /**
   * Retrieves a control record by reference id.
   *
   * @param referenceId the control-record id
   * @return the control record
   * @throws NotFoundException if no record exists for the reference id
   */
  public FraudEvaluation retrieve(UUID referenceId) {
    return FraudEvaluation.findByReference(referenceId)
        .orElseThrow(() -> new NotFoundException("No fraud evaluation with reference " + referenceId));
  }

  /**
   * Runs AI triage against a control record and persists the verdict, moving it to EVALUATED.
   *
   * @param referenceId the control-record id
   * @return the updated control record
   */
  @Transactional
  public FraudEvaluation evaluate(UUID referenceId) {
    FraudEvaluation e = retrieve(referenceId);
    FraudVerdict verdict = triageService.triage(toTransactionJson(e));
    e.riskScore = verdict.riskScore();
    e.decision = verdict.decision();
    e.aiReasoning = verdict.reasoning();
    e.caseNarrative = verdict.caseNarrative();
    e.status = "EVALUATED";
    e.updatedAt = Instant.now();
    return e;
  }

  /**
   * Applies an analyst disposition override and moves the record to DISPOSED.
   *
   * @param referenceId the control-record id
   * @param request the override payload
   * @return the updated control record
   */
  @Transactional
  public FraudEvaluation update(UUID referenceId, UpdateRequest request) {
    FraudEvaluation e = retrieve(referenceId);
    if (request.decision() != null) {
      e.decision = request.decision();
    }
    if (request.caseNarrative() != null) {
      e.caseNarrative = request.caseNarrative();
    }
    if (request.riskScore() != null) {
      e.riskScore = request.riskScore();
    }
    e.status = "DISPOSED";
    e.updatedAt = Instant.now();
    return e;
  }

  /**
   * Emits and logs a fraud alert for a control record.
   *
   * @param referenceId the control-record id
   * @param message an optional message; a default is used when null/blank
   * @return the control record the alert was raised for
   */
  public FraudEvaluation notify(UUID referenceId, String message) {
    FraudEvaluation e = retrieve(referenceId);
    String text =
        (message == null || message.isBlank())
            ? "Fraud alert raised for transaction " + e.transactionId
            : message;
    alertEvent.fire(
        new AlertEvent(e.referenceId, e.transactionId, e.decision, e.riskScore, text));
    return e;
  }

  /**
   * Serialises the transaction-relevant fields of a control record to JSON for the AI agent.
   *
   * @param e the control record
   * @return a JSON object string
   */
  private String toTransactionJson(FraudEvaluation e) {
    Map<String, Object> txn = new LinkedHashMap<>();
    txn.put("transactionId", e.transactionId);
    txn.put("type", e.type);
    txn.put("amount", e.amount);
    txn.put("nameOrig", e.nameOrig);
    txn.put("nameDest", e.nameDest);
    txn.put("oldbalanceOrg", e.oldbalanceOrg);
    txn.put("newbalanceOrig", e.newbalanceOrig);
    try {
      return objectMapper.writeValueAsString(txn);
    } catch (JsonProcessingException ex) {
      return txn.toString();
    }
  }
}
