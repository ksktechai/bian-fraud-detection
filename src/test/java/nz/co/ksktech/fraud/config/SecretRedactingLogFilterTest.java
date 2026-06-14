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
  void redactsBareGoogleApiKeyAnywhere() {
    LogRecord r = new LogRecord(Level.INFO, "configured key AIzaSyABCDEFGHIJKLMNOPqrstuvwx loaded");
    filter.isLoggable(r);
    assertThat(r.getMessage()).doesNotContain("AIzaSyABCDEFGHIJKLMNOPqrstuvwx");
    assertThat(r.getMessage()).contains("AIza***REDACTED***");
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
