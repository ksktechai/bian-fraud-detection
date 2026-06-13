package nz.co.ksktech.fraud.ai;

/**
 * Structured outcome of an AI fraud triage.
 *
 * @param riskScore 0-100 risk score
 * @param decision CLEAR | REVIEW | BLOCK
 * @param reasoning short rationale for the decision
 * @param caseNarrative 2-3 sentence narrative suitable for an analyst case file
 */
public record FraudVerdict(int riskScore, String decision, String reasoning, String caseNarrative) {

  /**
   * Builds the safe fallback verdict used when the model output cannot be parsed: REVIEW, neutral
   * score, with the raw model text preserved in {@code reasoning} for the analyst.
   *
   * @param rawText the unparsed model response
   * @return a REVIEW verdict carrying the raw text
   */
  public static FraudVerdict fallback(String rawText) {
    return new FraudVerdict(
        50, "REVIEW", rawText, "AI output could not be parsed; routed to manual review.");
  }
}
