package nz.co.ksktech.fraud.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * LangChain4j fraud-analyst agent backed by Google Gemini. It returns the model's raw response;
 * {@link FraudTriageService} is responsible for safely parsing that text into a {@link
 * FraudVerdict}, so a malformed model reply never breaks the evaluation flow.
 */
@RegisterAiService
public interface FraudTriageAgent {

  /**
   * Triages a single transaction.
   *
   * @param transactionJson the transaction serialised as JSON
   * @return the model's raw response, expected to be a JSON object (see system prompt)
   */
  @SystemMessage(
      """
      You are a senior fraud analyst reviewing a single payment transaction.
      Assess the fraud risk and respond with a SINGLE JSON object and nothing else
      (no markdown, no code fences, no commentary) using exactly these fields:
        "riskScore":    integer 0-100 (higher = more likely fraud),
        "decision":     one of "CLEAR", "REVIEW", "BLOCK",
        "reasoning":    a short one-sentence rationale,
        "caseNarrative": 2-3 sentences suitable for an analyst case file.
      Consider balance inconsistencies, large TRANSFER/CASH_OUT amounts, and accounts
      that are fully drained. Return ONLY the JSON object.
      """)
  @UserMessage("Transaction:\n{transactionJson}")
  String triage(@V("transactionJson") String transactionJson);
}
