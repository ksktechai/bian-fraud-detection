package nz.co.ksktech.fraud.testsupport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Helpers for stubbing the Gemini {@code generateContent} endpoint on the {@link
 * WireMockTestResource} server. The model "reply" is whatever text we put in the single response
 * part — letting each test feed the {@link nz.co.ksktech.fraud.ai.FraudTriageService} a clean JSON
 * verdict, a fenced/prose-wrapped verdict, or deliberate garbage.
 */
public final class GeminiStubs {

  /**
   * WireMock path regex for the Gemini generateContent endpoint. Version-agnostic: langchain4j
   * 1.4.x hit {@code /v1beta/models/{model}:generateContent} but 1.10.x (with a base-url override)
   * hits {@code /models/{model}:generateContent}, so the {@code v1beta} segment is optional.
   */
  public static final String GENERATE_CONTENT_PATH = "/(v1beta/)?models/.*:generateContent";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GeminiStubs() {}

  /** Builds a Gemini {@code GenerateContentResponse} JSON envelope wrapping {@code modelText}. */
  public static String generateContentBody(String modelText) {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode candidate = root.putArray("candidates").addObject();
    ObjectNode content = candidate.putObject("content");
    content.put("role", "model");
    content.putArray("parts").addObject().put("text", modelText);
    candidate.put("finishReason", "STOP");
    ObjectNode usage = root.putObject("usageMetadata");
    usage.put("promptTokenCount", 100);
    usage.put("candidatesTokenCount", 40);
    usage.put("totalTokenCount", 140);
    return root.toString();
  }

  /** Stubs the endpoint to return {@code modelText} as the model's raw reply. */
  public static void stubModelText(String modelText) {
    WireMockTestResource.server()
        .stubFor(
            post(urlPathMatching(GENERATE_CONTENT_PATH))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(generateContentBody(modelText))));
  }

  /** Stubs the endpoint to return a clean fraud-verdict JSON object. */
  public static void stubVerdict(int riskScore, String decision, String reasoning, String narrative) {
    stubModelText(verdictJson(riskScore, decision, reasoning, narrative));
  }

  /** A clean JSON verdict string (as the model is instructed to return). */
  public static String verdictJson(int riskScore, String decision, String reasoning, String narrative) {
    ObjectNode v = MAPPER.createObjectNode();
    v.put("riskScore", riskScore);
    v.put("decision", decision);
    v.put("reasoning", reasoning);
    v.put("caseNarrative", narrative);
    return v.toString();
  }
}
