package nz.co.ksktech.fraud.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Attaches a {@link SecretRedactingLogFilter} to every console/root log handler at startup,
 * composing with any filter already present. Done programmatically because the declarative
 * {@code quarkus.log.console.filter} + {@code @LoggingFilter} mechanism did not reliably attach the
 * filter in this Quarkus version (verified by {@code SecretRedactionWiringTest}).
 *
 * <p>The filter is built with the <em>current</em> configured secret values (the Gemini API key),
 * so it tracks key rotations — there is no hard-coded key format anywhere.
 */
@ApplicationScoped
public class LogRedactionInstaller {

  void onStart(@Observes StartupEvent event) {
    SecretRedactingLogFilter redactor = new SecretRedactingLogFilter(configuredSecrets());
    install(LogManager.getLogManager().getLogger(""), redactor);
  }

  /** The secret values to mask, read from the running config (so rotation is picked up). */
  private static List<String> configuredSecrets() {
    List<String> secrets = new ArrayList<>();
    var config = ConfigProvider.getConfig();
    config
        .getOptionalValue("quarkus.langchain4j.ai.gemini.api-key", String.class)
        .ifPresent(secrets::add);
    return secrets;
  }

  private static void install(Logger logger, SecretRedactingLogFilter redactor) {
    if (logger == null) {
      return;
    }
    for (Handler handler : logger.getHandlers()) {
      install(handler, redactor);
    }
  }

  private static void install(Handler handler, SecretRedactingLogFilter redactor) {
    Filter existing = handler.getFilter();
    if (existing instanceof RedactingFilter) {
      return; // already installed
    }
    handler.setFilter(new RedactingFilter(redactor, existing));
  }

  /** Runs the redactor (which mutates the record), then delegates the keep/drop decision. */
  private record RedactingFilter(SecretRedactingLogFilter redactor, Filter delegate)
      implements Filter {
    @Override
    public boolean isLoggable(LogRecord record) {
      redactor.isLoggable(record); // mutates message/parameters in place
      return delegate == null || delegate.isLoggable(record);
    }
  }
}
