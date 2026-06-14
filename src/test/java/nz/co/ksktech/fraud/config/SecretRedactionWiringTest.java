package nz.co.ksktech.fraud.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

/**
 * Proves end-to-end that the Gemini API key is redacted in the LIVE logging pipeline — not just
 * that {@link SecretRedactingLogFilter}'s logic is correct in isolation. It builds the exact record
 * the langchain4j {@code AiClientLogger} produces (a jboss {@link ExtLogRecord}, PRINTF style, with
 * the key-bearing URL as a parameter), runs it through a real attached handler, and checks the
 * FORMATTED output. If redaction regresses (e.g. the installer stops attaching), this fails.
 */
@QuarkusTest
class SecretRedactionWiringTest {

  private static final String FAKE_KEY = "AQ.Fake000ExampleNotARealKey00000000000000";
  private static final String URL_WITH_KEY =
      "https://generativelanguage.googleapis.com/v1beta/models/m:generateContent?key=" + FAKE_KEY;

  @Test
  void aiClientLoggerStyleRequestIsRedactedInFormattedOutput() {
    List<Handler> handlers = new ArrayList<>();
    collect(LogManager.getLogManager().getLogger(""), handlers);

    boolean someHandlerRedacts = false;
    for (Handler h : handlers) {
      if (h.getFilter() == null) {
        continue;
      }
      // exact shape of AiGeminiRestApi$AiClientLogger: infof("...- url: %s...", url)
      ExtLogRecord rec =
          new ExtLogRecord(Level.INFO, "Request:\n- url: %s", ExtLogRecord.FormatStyle.PRINTF, "test");
      rec.setParameters(new Object[] {URL_WITH_KEY});

      h.getFilter().isLoggable(rec); // run the (composed) handler filter — redacts in place

      String formatted = rec.getFormattedMessage();
      if (!formatted.contains(FAKE_KEY)) {
        assertThat(formatted).contains("key=***REDACTED***");
        someHandlerRedacts = true;
      }
    }

    assertThat(someHandlerRedacts)
        .as("a live log handler must redact the Gemini API key from request URLs")
        .isTrue();
  }

  private static void collect(Logger logger, List<Handler> out) {
    if (logger == null) {
      return;
    }
    for (Handler h : logger.getHandlers()) {
      collect(h, out);
    }
  }

  private static void collect(Handler handler, List<Handler> out) {
    if (handler == null) {
      return;
    }
    out.add(handler);
    try {
      Object nested = handler.getClass().getMethod("getHandlers").invoke(handler);
      if (nested instanceof Handler[] handlers) {
        for (Handler n : handlers) {
          collect(n, out);
        }
      }
    } catch (ReflectiveOperationException ignored) {
      // no nested handlers
    }
  }
}
