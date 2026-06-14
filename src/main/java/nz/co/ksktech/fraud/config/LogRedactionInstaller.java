package nz.co.ksktech.fraud.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Attaches {@link SecretRedactingLogFilter} to every console/root log handler at startup, composing
 * with any filter already present. Done programmatically because the declarative
 * {@code quarkus.log.console.filter} + {@code @LoggingFilter} mechanism did not reliably attach the
 * filter in this Quarkus version (verified by {@code SecretRedactionWiringTest}).
 *
 * <p>This is defense-in-depth: the primary protection is that the secret-bearing built-in HTTP
 * loggers are disabled and prompts/responses are logged via {@link LlmTrafficLogger} instead.
 */
@ApplicationScoped
public class LogRedactionInstaller {

  private static final SecretRedactingLogFilter REDACTOR = new SecretRedactingLogFilter();

  void onStart(@Observes StartupEvent event) {
    install(LogManager.getLogManager().getLogger(""));
  }

  private static void install(Logger logger) {
    if (logger == null) {
      return;
    }
    for (Handler handler : logger.getHandlers()) {
      install(handler);
    }
  }

  private static void install(Handler handler) {
    Filter existing = handler.getFilter();
    if (existing instanceof RedactingFilter) {
      return; // already installed
    }
    handler.setFilter(new RedactingFilter(existing));
  }

  /** Runs the redactor (which mutates the record), then delegates the keep/drop decision. */
  private record RedactingFilter(Filter delegate) implements Filter {
    @Override
    public boolean isLoggable(LogRecord record) {
      REDACTOR.isLoggable(record); // mutates message/parameters in place
      return delegate == null || delegate.isLoggable(record);
    }
  }
}
