package nz.co.ksktech.fraud.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nz.co.ksktech.fraud.ai.FraudTriageAgent;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@code /evaluate} runs AI triage and persists the verdict. The {@link
 * FraudTriageAgent} (the LLM-backed bean) is mocked to return a deterministic JSON reply so the test
 * is offline and reproducible — exercising the real safe-parsing + persistence path.
 */
@QuarkusTest
class FraudEvaluateTest {

  @InjectMock FraudTriageAgent agent;

  @Test
  void evaluateSetsRiskScoreAndDecision() {
    when(agent.triage(anyString()))
        .thenReturn(
            """
            {
              "riskScore": 87,
              "decision": "BLOCK",
              "reasoning": "Account fully drained on a large TRANSFER.",
              "caseNarrative": "A high-value transfer emptied the originating account. The pattern matches account-takeover cash-out. Recommend blocking and contacting the customer."
            }
            """);

    String ref =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "transactionId": "TXN-2002",
                  "amount": 9000000.00,
                  "type": "TRANSFER",
                  "nameOrig": "C9999",
                  "nameDest": "C8888",
                  "oldbalanceOrg": 9000000.00,
                  "newbalanceOrig": 0.00
                }
                """)
            .when()
            .post("/fraud-detection/initiate")
            .then()
            .statusCode(201)
            .extract()
            .path("referenceId");

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("riskScore", equalTo(87))
        .body("decision", equalTo("BLOCK"))
        .body("status", equalTo("EVALUATED"));
  }

  @Test
  void unparseableResponseDefaultsToReview() {
    when(agent.triage(anyString())).thenReturn("the model rambled without returning json");

    String ref =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {"transactionId":"TXN-3003","amount":10.0,"type":"PAYMENT",
                 "nameOrig":"C1","nameDest":"M2","oldbalanceOrg":10.0,"newbalanceOrig":0.0}
                """)
            .when()
            .post("/fraud-detection/initiate")
            .then()
            .statusCode(201)
            .extract()
            .path("referenceId");

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("decision", equalTo("REVIEW"))
        .body("aiReasoning", equalTo("the model rambled without returning json"));
  }
}
