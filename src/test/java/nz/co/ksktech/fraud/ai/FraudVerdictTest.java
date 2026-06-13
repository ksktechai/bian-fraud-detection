package nz.co.ksktech.fraud.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for the {@link FraudVerdict} record and its safe fallback factory. */
class FraudVerdictTest {

  @Test
  void fallbackIsNeutralReviewCarryingRawText() {
    FraudVerdict v = FraudVerdict.fallback("raw model output");
    assertThat(v.decision()).isEqualTo("REVIEW");
    assertThat(v.riskScore()).isEqualTo(50);
    assertThat(v.reasoning()).isEqualTo("raw model output");
    assertThat(v.caseNarrative()).contains("could not be parsed");
  }

  @Test
  void recordExposesAllComponents() {
    FraudVerdict v = new FraudVerdict(80, "BLOCK", "why", "narrative");
    assertThat(v.riskScore()).isEqualTo(80);
    assertThat(v.decision()).isEqualTo("BLOCK");
    assertThat(v.reasoning()).isEqualTo("why");
    assertThat(v.caseNarrative()).isEqualTo("narrative");
  }
}
