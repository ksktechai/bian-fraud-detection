package nz.co.ksktech.fraud.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Proves that secret redaction is actually wired into the live logging pipeline at runtime (via
 * {@link LogRedactionInstaller}) — not just that {@link SecretRedactingLogFilter}'s logic is
 * correct. If this regresses, the Gemini API key could leak into logs again. The check is
 * behavioural: it feeds a key-bearing record (key in a parameter, as the langchain4j logger does)
 * through each attached handler filter and asserts at least one redacts it.
 */
@QuarkusTest
class SecretRedactionWiringTest {

  @Test
  void aHandlerFilterRedactsTheApiKeyAtRuntime() {
    List<Filter> filters = new ArrayList<>();
    collect(LogManager.getLogManager().getLogger(""), filters);

    assertThat(filters).as("expected at least one handler filter to be attached").isNotEmpty();
    assertThat(filters).anyMatch(SecretRedactionWiringTest::redactsKey);
  }

  private static boolean redactsKey(Filter filter) {
    LogRecord r = new LogRecord(Level.INFO, "Request:\n- url: %s");
    r.setParameters(
        new Object[] {
          "https://generativelanguage.googleapis.com/v1beta/models/m:generateContent"
              + "?key=AQ.Fake000ExampleNotARealKey00000000000000"
        });
    filter.isLoggable(r);
    String url = String.valueOf(r.getParameters()[0]);
    return !url.contains("AQ.Fake000ExampleNotARealKey00000000000000");
  }

  private static void collect(Logger logger, List<Filter> out) {
    if (logger == null) {
      return;
    }
    for (Handler h : logger.getHandlers()) {
      collect(h, out);
    }
  }

  private static void collect(Handler handler, List<Filter> out) {
    if (handler == null) {
      return;
    }
    if (handler.getFilter() != null) {
      out.add(handler.getFilter());
    }
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
