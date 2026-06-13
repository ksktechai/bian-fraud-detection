package nz.co.ksktech.fraud.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Verifies the BIAN lifecycle persistence paths that do NOT need the LLM: initiate, retrieve,
 * update (analyst disposition) and notify, plus the not-found cases. The AI {@code /evaluate} path
 * is covered by {@link FraudEvaluateTest} (mocked agent) and {@link FraudTriageWireMockTest}
 * (real langchain4j path over WireMock).
 */
@QuarkusTest
class FraudDetectionResourceTest {

  /** Initiates a default control record and returns its reference id. */
  private static String initiate() {
    return given()
        .contentType(ContentType.JSON)
        .body(
            """
            {"transactionId":"TXN-LC","amount":181.00,"type":"TRANSFER",
             "nameOrig":"C1","nameDest":"C2","oldbalanceOrg":181.00,"newbalanceOrig":0.00}
            """)
        .when()
        .post("/fraud-detection/initiate")
        .then()
        .statusCode(201)
        .extract()
        .path("referenceId");
  }

  @Test
  void initiateThenRetrieveReturnsControlRecord() {
    String ref =
        given()
            .contentType(ContentType.JSON)
            .body(
                """
                {
                  "transactionId": "TXN-1001",
                  "amount": 181.00,
                  "type": "TRANSFER",
                  "nameOrig": "C1231006815",
                  "nameDest": "C1979787155",
                  "oldbalanceOrg": 181.00,
                  "newbalanceOrig": 0.00
                }
                """)
            .when()
            .post("/fraud-detection/initiate")
            .then()
            .statusCode(201)
            .header("X-Correlation-Id", notNullValue())
            .body("referenceId", notNullValue())
            .body("status", equalTo("INITIATED"))
            .body("transactionId", equalTo("TXN-1001"))
            .extract()
            .path("referenceId");

    given()
        .when()
        .get("/fraud-detection/{ref}/retrieve", ref)
        .then()
        .statusCode(200)
        .body("referenceId", equalTo(ref))
        .body("status", equalTo("INITIATED"))
        .body("type", equalTo("TRANSFER"));
  }

  @Test
  void updateAppliesAnalystOverrideAndDisposes() {
    String ref = initiate();

    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {"decision":"CLEAR","caseNarrative":"Confirmed legitimate with customer.","riskScore":5}
            """)
        .when()
        .put("/fraud-detection/{ref}/update", ref)
        .then()
        .statusCode(200)
        .body("decision", equalTo("CLEAR"))
        .body("riskScore", equalTo(5))
        .body("caseNarrative", equalTo("Confirmed legitimate with customer."))
        .body("status", equalTo("DISPOSED"));

    // override persisted
    given()
        .when()
        .get("/fraud-detection/{ref}/retrieve", ref)
        .then()
        .statusCode(200)
        .body("decision", equalTo("CLEAR"))
        .body("status", equalTo("DISPOSED"));
  }

  @Test
  void updateWithNullFieldsLeavesThemUntouched() {
    String ref = initiate();

    given()
        .contentType(ContentType.JSON)
        .body("{\"decision\":\"BLOCK\"}")
        .when()
        .put("/fraud-detection/{ref}/update", ref)
        .then()
        .statusCode(200)
        .body("decision", equalTo("BLOCK"))
        .body("status", equalTo("DISPOSED"))
        .body("caseNarrative", nullValue())
        .body("riskScore", nullValue());
  }

  @Test
  void notifyWithDefaultMessageReturnsControlRecord() {
    String ref = initiate();

    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .post("/fraud-detection/{ref}/notify", ref)
        .then()
        .statusCode(200)
        .body("referenceId", equalTo(ref))
        .header("X-Correlation-Id", notNullValue());
  }

  @Test
  void notifyWithCustomMessageReturnsControlRecord() {
    String ref = initiate();

    given()
        .contentType(ContentType.JSON)
        .body("{\"message\":\"Escalated to the financial crime team.\"}")
        .when()
        .post("/fraud-detection/{ref}/notify", ref)
        .then()
        .statusCode(200)
        .body("referenceId", equalTo(ref));
  }

  @Test
  void retrieveUnknownReferenceIs404() {
    given()
        .when()
        .get("/fraud-detection/{ref}/retrieve", "00000000-0000-0000-0000-000000000000")
        .then()
        .statusCode(404);
  }

  @Test
  void updateUnknownReferenceIs404() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"decision\":\"CLEAR\"}")
        .when()
        .put("/fraud-detection/{ref}/update", "00000000-0000-0000-0000-000000000000")
        .then()
        .statusCode(404);
  }

  @Test
  void notifyUnknownReferenceIs404() {
    given()
        .contentType(ContentType.JSON)
        .body("{}")
        .when()
        .post("/fraud-detection/{ref}/notify", "00000000-0000-0000-0000-000000000000")
        .then()
        .statusCode(404);
  }
}
