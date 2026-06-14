package nz.co.ksktech.fraud.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SecretRedactingLogFilter} — redaction must not depend on the key format. */
class SecretRedactingLogFilterTest {

  private final SecretRedactingLogFilter noSecrets = new SecretRedactingLogFilter(List.of());

  @Test
  void redactsKeyQueryParamWhateverTheKeyFormat() {
    // legacy AIza..., newer AQ...., and an arbitrary future format all masked the same way
    for (String key :
        List.of("AIzaSyABCDEFG12345", "AQ.Fake000NotARealKey00000000", "xptoWHATEVER_2031.format")) {
      LogRecord r =
          new LogRecord(Level.INFO, "url: https://host/v1beta/models/m:generateContent?key=" + key);
      noSecrets.isLoggable(r);
      assertThat(r.getMessage()).doesNotContain(key);
      assertThat(r.getMessage()).contains("key=***REDACTED***");
    }
  }

  @Test
  void redactsKeyHeldInLogRecordParameters() {
    // reproduces the langchain4j AiClientLogger case: infof("...- url: %s...", url)
    LogRecord r = new LogRecord(Level.INFO, "Request:\n- method: %s\n- url: %s");
    r.setParameters(
        new Object[] {
          "POST", "https://host/v1beta/models/m:generateContent?key=AQ.Fake000NotARealKey00000000"
        });

    noSecrets.isLoggable(r);

    String url = (String) r.getParameters()[1];
    assertThat(url).doesNotContain("AQ.Fake000NotARealKey00000000");
    assertThat(url).contains("key=***REDACTED***");
    assertThat(r.getParameters()[0]).isEqualTo("POST"); // untouched
  }

  @Test
  void redactsConfiguredSecretValueAnywhereRegardlessOfFormat() {
    String rotatedKey = "totally-new-format-secret-value-1234567890";
    SecretRedactingLogFilter filter = new SecretRedactingLogFilter(List.of(rotatedKey));

    LogRecord r = new LogRecord(Level.INFO, "loaded GEMINI_API_KEY=" + rotatedKey + " from env");
    filter.isLoggable(r);

    assertThat(r.getMessage()).doesNotContain(rotatedKey);
    assertThat(r.getMessage()).contains("***REDACTED***");
  }

  @Test
  void blankOrShortConfiguredSecretIsIgnored() {
    // an empty/short secret must NOT cause mass redaction of every log line
    SecretRedactingLogFilter filter = new SecretRedactingLogFilter(List.of("", "abc"));
    LogRecord r = new LogRecord(Level.INFO, "an ordinary log line with no secrets");
    filter.isLoggable(r);
    assertThat(r.getMessage()).isEqualTo("an ordinary log line with no secrets");
  }

  @Test
  void leavesOrdinaryMessagesUntouched() {
    LogRecord r = new LogRecord(Level.INFO, ">>> POST /fraud-detection/initiate -> 201 (4 ms)");
    noSecrets.isLoggable(r);
    assertThat(r.getMessage()).isEqualTo(">>> POST /fraud-detection/initiate -> 201 (4 ms)");
  }

  @Test
  void nullMessageIsSafe() {
    LogRecord r = new LogRecord(Level.INFO, null);
    assertThat(noSecrets.isLoggable(r)).isTrue();
  }
}
