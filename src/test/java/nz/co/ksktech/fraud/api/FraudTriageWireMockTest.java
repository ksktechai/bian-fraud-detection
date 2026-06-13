package nz.co.ksktech.fraud.api;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nz.co.ksktech.fraud.testsupport.GeminiStubs;
import nz.co.ksktech.fraud.testsupport.WireMockTestResource;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@code /evaluate} end-to-end through the REAL FraudTriageAgent → quarkus-langchain4j →
 * Gemini HTTP client, with WireMock standing in for the Gemini API. Unlike {@link FraudEvaluateTest}
 * (which mocks the agent bean), this proves the langchain4j wiring, request shape, and the
 * defensive JSON parsing against actual on-the-wire model responses.
 */
@QuarkusTest
@QuarkusTestResource(WireMockTestResource.class)
class FraudTriageWireMockTest {

  /** Initiates a control record and returns its reference id. */
  private static String initiate(String type, double amount, double oldBal, double newBal) {
    return given()
        .contentType(ContentType.JSON)
        .body(
            """
            {"transactionId":"TXN-WM","amount":%s,"type":"%s",
             "nameOrig":"C1","nameDest":"C2","oldbalanceOrg":%s,"newbalanceOrig":%s}
            """
                .formatted(amount, type, oldBal, newBal))
        .when()
        .post("/fraud-detection/initiate")
        .then()
        .statusCode(201)
        .extract()
        .path("referenceId");
  }

  @Test
  void evaluateParsesBlockVerdictFromGemini() {
    GeminiStubs.stubVerdict(
        92, "BLOCK", "Account fully drained on a large transfer.", "High-value transfer emptied the account.");
    String ref = initiate("TRANSFER", 9_000_000.00, 9_000_000.00, 0.00);

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("riskScore", equalTo(92))
        .body("decision", equalTo("BLOCK"))
        .body("status", equalTo("EVALUATED"))
        .body("aiReasoning", containsString("drained"));

    // the real Gemini HTTP client actually hit WireMock
    WireMockTestResource.server()
        .verify(postRequestedFor(urlPathMatching(GeminiStubs.GENERATE_CONTENT_PATH)));
  }

  @Test
  void evaluateParsesLowRiskClearVerdict() {
    GeminiStubs.stubVerdict(4, "CLEAR", "Small routine payment to a known merchant.", "Looks legitimate.");
    String ref = initiate("PAYMENT", 12.50, 500.00, 487.50);

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("riskScore", equalTo(4))
        .body("decision", equalTo("CLEAR"));
  }

  @Test
  void evaluateParsesJsonWrappedInMarkdownFenceAndProse() {
    GeminiStubs.stubModelText(
        """
        Sure — here is my assessment:
        ```json
        {"riskScore": 63, "decision": "REVIEW", "reasoning": "Unusual amount for this account.",
         "caseNarrative": "The transfer is larger than typical activity and warrants a look."}
        ```
        Let me know if you need more detail.
        """);
    String ref = initiate("TRANSFER", 4200.00, 5000.00, 800.00);

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("riskScore", equalTo(63))
        .body("decision", equalTo("REVIEW"));
  }

  @Test
  void evaluateClampsOutOfRangeRiskScore() {
    GeminiStubs.stubModelText(
        "{\"riskScore\": 250, \"decision\": \"BLOCK\", \"reasoning\": \"x\", \"caseNarrative\": \"y\"}");
    String ref = initiate("CASH_OUT", 800_000.00, 800_000.00, 0.00);

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("riskScore", equalTo(100));
  }

  @Test
  void evaluateFallsBackToReviewOnNonJsonResponse() {
    GeminiStubs.stubModelText("I'm not sure, this transaction looks fine to me honestly.");
    String ref = initiate("PAYMENT", 30.00, 30.00, 0.00);

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("decision", equalTo("REVIEW"))
        .body("aiReasoning", containsString("not sure"));
  }

  @Test
  void unknownDecisionValueNormalisesToReview() {
    GeminiStubs.stubModelText(
        "{\"riskScore\": 70, \"decision\": \"FREEZE\", \"reasoning\": \"r\", \"caseNarrative\": \"n\"}");
    String ref = initiate("TRANSFER", 1000.00, 1000.00, 0.00);

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/fraud-detection/{ref}/evaluate", ref)
        .then()
        .statusCode(200)
        .body("riskScore", equalTo(70))
        .body("decision", equalTo("REVIEW"));
  }
}
