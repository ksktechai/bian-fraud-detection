package nz.co.ksktech.fraud.config;

import io.quarkus.logging.LoggingFilter;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Masks secrets in console log output. The quarkus-langchain4j Gemini client passes the API key as
 * a {@code ?key=...} URL query parameter and its request logger prints the full URL, so without this
 * the key would appear in plaintext logs. Attached via {@code quarkus.log.console.filter=redact-secrets}.
 *
 * <p>Redacts both the {@code key=} query parameter and any bare Google API key ({@code AIza...}),
 * so the key is masked regardless of how it surfaces.
 */
@LoggingFilter(name = "redact-secrets")
public final class SecretRedactingLogFilter implements Filter {

  // ?key=... or &key=... (value up to the next & or whitespace) — masks any key format
  private static final Pattern KEY_PARAM = Pattern.compile("([?&]key=)[^&\\s\"]+");
  // bare Google API keys, anywhere in the message: legacy "AIza..." and newer "AQ.<base64url>"
  private static final Pattern GOOGLE_KEY =
      Pattern.compile("AIza[0-9A-Za-z_\\-]{10,}|AQ\\.[0-9A-Za-z_\\-]{20,}");

  @Override
  public boolean isLoggable(LogRecord record) {
    String message = record.getMessage();
    if (message == null) {
      return true;
    }
    // fast path: only run the regexes when there is something to redact
    if (message.indexOf("key=") >= 0 || message.indexOf("AIza") >= 0 || message.indexOf("AQ.") >= 0) {
      String masked = KEY_PARAM.matcher(message).replaceAll("$1***REDACTED***");
      masked = GOOGLE_KEY.matcher(masked).replaceAll("***REDACTED-KEY***");
      record.setMessage(masked);
    }
    return true;
  }
}
