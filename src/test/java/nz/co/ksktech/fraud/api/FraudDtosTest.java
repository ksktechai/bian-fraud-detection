package nz.co.ksktech.fraud.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import nz.co.ksktech.fraud.api.FraudDtos.EvaluationResponse;
import nz.co.ksktech.fraud.domain.FraudEvaluation;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EvaluationResponse#from(FraudEvaluation)} entity-to-DTO mapping. */
class FraudDtosTest {

  @Test
  void mapsEveryControlRecordField() {
    FraudEvaluation e = new FraudEvaluation();
    e.referenceId = UUID.randomUUID();
    e.transactionId = "TXN-42";
    e.amount = new BigDecimal("181.00");
    e.type = "TRANSFER";
    e.nameOrig = "C1";
    e.nameDest = "C2";
    e.oldbalanceOrg = new BigDecimal("181.00");
    e.newbalanceOrig = BigDecimal.ZERO;
    e.riskScore = 73;
    e.decision = "REVIEW";
    e.aiReasoning = "reasoning";
    e.caseNarrative = "narrative";
    e.status = "EVALUATED";
    e.createdAt = Instant.parse("2026-06-13T00:00:00Z");
    e.updatedAt = Instant.parse("2026-06-13T01:00:00Z");

    EvaluationResponse r = EvaluationResponse.from(e);

    assertThat(r.referenceId()).isEqualTo(e.referenceId);
    assertThat(r.transactionId()).isEqualTo("TXN-42");
    assertThat(r.amount()).isEqualByComparingTo("181.00");
    assertThat(r.type()).isEqualTo("TRANSFER");
    assertThat(r.nameOrig()).isEqualTo("C1");
    assertThat(r.nameDest()).isEqualTo("C2");
    assertThat(r.oldbalanceOrg()).isEqualByComparingTo("181.00");
    assertThat(r.newbalanceOrig()).isEqualByComparingTo("0");
    assertThat(r.riskScore()).isEqualTo(73);
    assertThat(r.decision()).isEqualTo("REVIEW");
    assertThat(r.aiReasoning()).isEqualTo("reasoning");
    assertThat(r.caseNarrative()).isEqualTo("narrative");
    assertThat(r.status()).isEqualTo("EVALUATED");
    assertThat(r.createdAt()).isEqualTo(e.createdAt);
    assertThat(r.updatedAt()).isEqualTo(e.updatedAt);
  }

  @Test
  void mapsNullableAiFieldsBeforeEvaluation() {
    FraudEvaluation e = new FraudEvaluation();
    e.referenceId = UUID.randomUUID();
    e.status = "INITIATED";

    EvaluationResponse r = EvaluationResponse.from(e);

    assertThat(r.status()).isEqualTo("INITIATED");
    assertThat(r.riskScore()).isNull();
    assertThat(r.decision()).isNull();
    assertThat(r.aiReasoning()).isNull();
  }
}
