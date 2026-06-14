package nz.co.ksktech.fraud.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SecretRedactingLogFilter} — the Gemini API key must never survive. */
class SecretRedactingLogFilterTest {

  private final SecretRedactingLogFilter filter = new SecretRedactingLogFilter();

  // NOTE: never use a real key here — this is a deliberately fake, well-formed placeholder.
  private static final String FAKE_KEY = "AIzaSyFAKE000ExampleNotARealKey00000000000";

  @Test
  void redactsKeyQueryParamInGeminiRequestUrl() {
    LogRecord r =
        new LogRecord(
            Level.INFO,
            "url: https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-3.1-flash-lite:generateContent?key=" + FAKE_KEY);

    assertThat(filter.isLoggable(r)).isTrue();
    assertThat(r.getMessage()).doesNotContain(FAKE_KEY);
    assertThat(r.getMessage()).contains("key=***REDACTED***");
  }

  @Test
  void redactsBareLegacyGoogleApiKey() {
    LogRecord r = new LogRecord(Level.INFO, "configured key AIzaSyABCDEFGHIJKLMNOPqrstuvwx loaded");
    filter.isLoggable(r);
    assertThat(r.getMessage()).doesNotContain("AIzaSyABCDEFGHIJKLMNOPqrstuvwx");
    assertThat(r.getMessage()).contains("***REDACTED-KEY***");
  }

  @Test
  void redactsBareNewFormatGoogleApiKey() {
    // newer "AQ.<base64url>" key format (fake placeholder)
    LogRecord r = new LogRecord(Level.INFO, "loaded AQ.Fake000ExampleNotARealKey00000000000000 from env");
    filter.isLoggable(r);
    assertThat(r.getMessage()).doesNotContain("AQ.Fake000ExampleNotARealKey00000000000000");
    assertThat(r.getMessage()).contains("***REDACTED-KEY***");
  }

  @Test
  void redactsKeyHeldInLogRecordParameters() {
    // reproduces the langchain4j AiClientLogger case: infof("...- url: %s...", url)
    // where the key lives in a parameter, not the message string
    LogRecord r = new LogRecord(Level.INFO, "Request:\n- method: %s\n- url: %s");
    r.setParameters(
        new Object[] {
          "POST",
          "https://generativelanguage.googleapis.com/v1beta/models/"
              + "gemini-3.1-flash-lite:generateContent?key=AQ.Fake000ExampleNotARealKey00000000000000"
        });

    filter.isLoggable(r);

    String url = (String) r.getParameters()[1];
    assertThat(url).doesNotContain("AQ.Fake000ExampleNotARealKey00000000000000");
    assertThat(url).contains("key=***REDACTED***");
    assertThat(r.getParameters()[0]).isEqualTo("POST"); // untouched
  }

  @Test
  void leavesOrdinaryMessagesUntouched() {
    LogRecord r = new LogRecord(Level.INFO, ">>> POST /fraud-detection/initiate -> 201 (4 ms)");
    filter.isLoggable(r);
    assertThat(r.getMessage()).isEqualTo(">>> POST /fraud-detection/initiate -> 201 (4 ms)");
  }

  @Test
  void nullMessageIsSafe() {
    LogRecord r = new LogRecord(Level.INFO, null);
    assertThat(filter.isLoggable(r)).isTrue();
  }
}
