package nz.co.ksktech.fraud.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * Boots a WireMock server and rewires the quarkus-langchain4j Gemini client to it, so WireMock-based
 * tests exercise the REAL agent → langchain4j → HTTP path without ever touching the Google Gemini
 * API. Mirrors the congress-trade-watcher / fundlens test-resource pattern.
 *
 * <p>The langchain4j ai-gemini client calls {@code {base-url}/v1beta/models/{modelId}:generateContent};
 * pointing {@code quarkus.langchain4j.ai.gemini.base-url} at this server lets {@link GeminiStubs}
 * decide what the "model" returns. The running server is exposed via {@link #server()} so tests can
 * re-stub per scenario and verify the exact request the client sent.
 */
public class WireMockTestResource implements QuarkusTestResourceLifecycleManager {

  private static WireMockServer wireMockServer;

  public static WireMockServer server() {
    return wireMockServer;
  }

  @Override
  public Map<String, String> start() {
    wireMockServer = new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    // a sane default verdict so any test that forgets to stub still gets valid JSON
    GeminiStubs.stubVerdict(40, "REVIEW", "Default stubbed triage.", "Default stubbed narrative.");

    return Map.of(
        "quarkus.langchain4j.ai.gemini.base-url", wireMockServer.baseUrl(),
        // a non-blank key so the client builds; WireMock ignores it
        "quarkus.langchain4j.ai.gemini.api-key", "test-gemini-key");
  }

  @Override
  public void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      wireMockServer = null;
    }
  }
}
