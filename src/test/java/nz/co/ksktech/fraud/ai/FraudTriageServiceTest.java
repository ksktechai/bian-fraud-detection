package nz.co.ksktech.fraud.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Pure (no-Quarkus) unit tests for the defensive JSON parsing in {@link FraudTriageService}. The
 * agent is mocked, so these run in milliseconds and cover every branch of {@code parse()}.
 */
class FraudTriageServiceTest {

  private final FraudTriageAgent agent = mock(FraudTriageAgent.class);
  private final FraudTriageService service = new FraudTriageService(agent, new ObjectMapper());

  @Test
  void parsesCleanJsonVerdict() {
    FraudVerdict v =
        service.parse(
            "{\"riskScore\":87,\"decision\":\"BLOCK\",\"reasoning\":\"drained\",\"caseNarrative\":\"narrative\"}");
    assertThat(v.riskScore()).isEqualTo(87);
    assertThat(v.decision()).isEqualTo("BLOCK");
    assertThat(v.reasoning()).isEqualTo("drained");
    assertThat(v.caseNarrative()).isEqualTo("narrative");
  }

  @Test
  void extractsJsonWrappedInMarkdownFenceAndProse() {
    String raw =
        """
        Here you go:
        ```json
        {"riskScore": 30, "decision": "CLEAR", "reasoning": "fine", "caseNarrative": "ok"}
        ```
        cheers
        """;
    FraudVerdict v = service.parse(raw);
    assertThat(v.riskScore()).isEqualTo(30);
    assertThat(v.decision()).isEqualTo("CLEAR");
  }

  @Test
  void lowercaseDecisionIsUppercased() {
    FraudVerdict v =
        service.parse("{\"riskScore\":10,\"decision\":\"clear\",\"reasoning\":\"\",\"caseNarrative\":\"\"}");
    assertThat(v.decision()).isEqualTo("CLEAR");
  }

  @Test
  void unknownDecisionFallsBackToReview() {
    FraudVerdict v =
        service.parse("{\"riskScore\":70,\"decision\":\"FREEZE\",\"reasoning\":\"r\",\"caseNarrative\":\"n\"}");
    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.riskScore()).isEqualTo(70);
  }

  @Test
  void riskScoreAboveRangeIsClampedTo100() {
    FraudVerdict v =
        service.parse("{\"riskScore\":250,\"decision\":\"BLOCK\",\"reasoning\":\"\",\"caseNarrative\":\"\"}");
    assertThat(v.riskScore()).isEqualTo(100);
  }

  @Test
  void riskScoreBelowRangeIsClampedTo0() {
    FraudVerdict v =
        service.parse("{\"riskScore\":-5,\"decision\":\"CLEAR\",\"reasoning\":\"\",\"caseNarrative\":\"\"}");
    assertThat(v.riskScore()).isZero();
  }

  @Test
  void missingFieldsGetSafeDefaults() {
    FraudVerdict v = service.parse("{\"reasoning\":\"only reasoning present\"}");
    assertThat(v.riskScore()).isEqualTo(50);
    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.reasoning()).isEqualTo("only reasoning present");
    assertThat(v.caseNarrative()).isEmpty();
  }

  @Test
  void nonJsonTextFallsBackToReviewWithRawText() {
    FraudVerdict v = service.parse("the model just rambled");
    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.riskScore()).isEqualTo(50);
    assertThat(v.reasoning()).isEqualTo("the model just rambled");
  }

  @Test
  void nullInputFallsBackToReview() {
    FraudVerdict v = service.parse(null);
    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.reasoning()).isEmpty();
  }

  @Test
  void blankInputFallsBackToReview() {
    FraudVerdict v = service.parse("   ");
    assertThat(v.decision()).isEqualTo("REVIEW");
  }

  @Test
  void brokenJsonInsideBracesFallsBackToReviewWithRaw() {
    String raw = "{ this is not valid json, decision: BLOCK";
    FraudVerdict v = service.parse(raw);
    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.reasoning()).isEqualTo(raw.strip());
  }

  @Test
  void triageDelegatesToAgentThenParses() {
    when(agent.triage(anyString()))
        .thenReturn("{\"riskScore\":15,\"decision\":\"CLEAR\",\"reasoning\":\"r\",\"caseNarrative\":\"n\"}");

    FraudVerdict v = service.triage("{\"transactionId\":\"T1\"}");

    assertThat(v.decision()).isEqualTo("CLEAR");
    assertThat(v.riskScore()).isEqualTo(15);
    verify(agent).triage("{\"transactionId\":\"T1\"}");
  }

  @Test
  void triageFallsBackToReviewWhenAgentThrows() {
    // e.g. Gemini 429 rate limit or 503 high-demand surfaced by the langchain4j client
    when(agent.triage(anyString()))
        .thenThrow(new RuntimeException("503 This model is currently experiencing high demand"));

    FraudVerdict v = service.triage("{\"transactionId\":\"T1\"}");

    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.riskScore()).isEqualTo(50);
    assertThat(v.reasoning()).contains("AI provider unavailable");
  }
}
