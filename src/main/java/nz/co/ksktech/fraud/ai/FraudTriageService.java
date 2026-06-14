package nz.co.ksktech.fraud.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * Wraps {@link FraudTriageAgent} and turns its free-form model output into a strongly-typed {@link
 * FraudVerdict}. Parsing is defensive: the model frequently wraps JSON in prose or code fences, so
 * we extract the first {@code { ... }} block and validate the decision. Any failure degrades
 * gracefully to a REVIEW verdict that preserves the raw text for the analyst.
 */
@ApplicationScoped
public class FraudTriageService {

  private static final Set<String> VALID_DECISIONS = Set.of("CLEAR", "REVIEW", "BLOCK");

  private final FraudTriageAgent agent;
  private final ObjectMapper objectMapper;

  /**
   * Constructor.
   *
   * @param agent the LangChain4j triage agent
   * @param objectMapper the JSON mapper
   */
  public FraudTriageService(FraudTriageAgent agent, ObjectMapper objectMapper) {
    this.agent = agent;
    this.objectMapper = objectMapper;
  }

  /**
   * Runs AI triage and parses the result into a {@link FraudVerdict}.
   *
   * @param transactionJson the transaction serialised as JSON
   * @return the parsed verdict, or a REVIEW fallback if the response cannot be parsed
   */
  public FraudVerdict triage(String transactionJson) {
    String raw;
    try {
      raw = agent.triage(transactionJson);
    } catch (RuntimeException e) {
      // The LLM call itself failed (rate limit / 503 high demand / timeout). Degrade safely to
      // REVIEW so a transient provider outage routes the case to an analyst instead of erroring.
      Log.warnf("Fraud triage LLM call failed, defaulting to REVIEW: %s", e.getMessage());
      return FraudVerdict.fallback("AI provider unavailable: " + e.getMessage());
    }
    return parse(raw);
  }

  /**
   * Safely parses a model response into a {@link FraudVerdict}.
   *
   * @param raw the raw model response
   * @return the parsed verdict, or {@link FraudVerdict#fallback(String)} on any failure
   */
  FraudVerdict parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return FraudVerdict.fallback("");
    }
    String json = extractJson(raw);
    try {
      JsonNode node = objectMapper.readTree(json);
      int riskScore = clamp(node.path("riskScore").asInt(50));
      String decision = node.path("decision").asText("REVIEW").trim().toUpperCase();
      if (!VALID_DECISIONS.contains(decision)) {
        decision = "REVIEW";
      }
      String reasoning = node.path("reasoning").asText("");
      String caseNarrative = node.path("caseNarrative").asText("");
      return new FraudVerdict(riskScore, decision, reasoning, caseNarrative);
    } catch (Exception e) {
      Log.warnf("Could not parse fraud triage response, defaulting to REVIEW: %s", e.getMessage());
      return FraudVerdict.fallback(raw.strip());
    }
  }

  /**
   * Extracts the first JSON object from a model response, tolerating surrounding prose or fences.
   *
   * @param raw the raw model response
   * @return the substring from the first {@code {} to the last {@code }}, or the input unchanged
   */
  private static String extractJson(String raw) {
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return raw.substring(start, end + 1);
    }
    return raw;
  }

  /**
   * Clamps a risk score into the 0-100 range.
   *
   * @param value the raw score
   * @return the clamped score
   */
  private static int clamp(int value) {
    return Math.max(0, Math.min(100, value));
  }
}
