package nz.co.ksktech.fraud.config;

import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Masks secrets in log output as a defense-in-depth net (the primary protection is not logging the
 * key at all — see {@link LlmTrafficLogger}). Redacts BOTH the message template and every String
 * parameter, because callers such as JBoss {@code Logger.infof("...- url: %s...", url)} keep the
 * value in a log-record parameter rather than the message string.
 *
 * <p>Redacts the {@code key=} query parameter (any key format) and bare Google API keys (legacy
 * {@code AIza...} and newer {@code AQ.<base64url>}). Attached to the console handler programmatically
 * by {@link LogRedactionInstaller} (the declarative {@code quarkus.log.console.filter} mechanism did
 * not reliably attach in this Quarkus version).
 */
public final class SecretRedactingLogFilter implements Filter {

  // ?key=... or &key=... (value up to the next & or whitespace) — masks any key format
  private static final Pattern KEY_PARAM = Pattern.compile("([?&]key=)[^&\\s\"]+");
  // bare Google API keys, anywhere: legacy "AIza..." and newer "AQ.<base64url>"
  private static final Pattern GOOGLE_KEY =
      Pattern.compile("AIza[0-9A-Za-z_\\-]{10,}|AQ\\.[0-9A-Za-z_\\-]{20,}");

  @Override
  public boolean isLoggable(LogRecord record) {
    String message = record.getMessage();
    if (mightContainSecret(message)) {
      record.setMessage(redact(message));
    }
    Object[] params = record.getParameters();
    if (params != null) {
      boolean changed = false;
      for (int i = 0; i < params.length; i++) {
        if (params[i] instanceof String s && mightContainSecret(s)) {
          params[i] = redact(s);
          changed = true;
        }
      }
      if (changed) {
        record.setParameters(params);
      }
    }
    return true;
  }

  /** Fast pre-check so the regexes only run when there is something worth redacting. */
  private static boolean mightContainSecret(String s) {
    return s != null && (s.contains("key=") || s.contains("AIza") || s.contains("AQ."));
  }

  private static String redact(String s) {
    String masked = KEY_PARAM.matcher(s).replaceAll("$1***REDACTED***");
    return GOOGLE_KEY.matcher(masked).replaceAll("***REDACTED-KEY***");
  }
}
