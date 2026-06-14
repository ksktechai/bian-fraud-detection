package nz.co.ksktech.fraud.config;

import java.util.Collection;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Masks secrets in log output. Redaction is <strong>format-agnostic</strong> so it keeps working
 * across key rotations no matter what the new key looks like:
 *
 * <ul>
 *   <li>any {@code key=...} query-parameter value — the langchain4j Gemini client puts the API key
 *       in the request URL ({@code ?key=...}); this masks the value whatever its format;
 *   <li>the exact configured secret value(s) wherever they appear (passed in by {@link
 *       LogRedactionInstaller} from the running config), so a bare occurrence of the current key is
 *       masked regardless of prefix/format.
 * </ul>
 *
 * <p>Redacts BOTH the message template and every String parameter, because callers such as JBoss
 * {@code Logger.infof("...- url: %s...", url)} keep the value in a log-record parameter rather than
 * the message string. Attached to the console handler by {@link LogRedactionInstaller}.
 */
public final class SecretRedactingLogFilter implements Filter {

  // ?key=... or &key=... (value up to the next & or whitespace) — masks any key format
  private static final Pattern KEY_PARAM = Pattern.compile("([?&]key=)[^&\\s\"]+");
  private static final String MASK = "***REDACTED***";

  private final List<String> secrets;

  /**
   * @param secrets exact secret values to mask wherever they appear; null/blank/short values are
   *     ignored (an empty secret would otherwise match everywhere)
   */
  public SecretRedactingLogFilter(Collection<String> secrets) {
    this.secrets =
        secrets.stream().filter(s -> s != null && s.length() >= 12).distinct().toList();
  }

  @Override
  public boolean isLoggable(LogRecord record) {
    String message = record.getMessage();
    if (message != null) {
      String redacted = redact(message);
      if (!redacted.equals(message)) {
        record.setMessage(redacted);
      }
    }
    Object[] params = record.getParameters();
    if (params != null) {
      boolean changed = false;
      for (int i = 0; i < params.length; i++) {
        if (params[i] instanceof String s) {
          String redacted = redact(s);
          if (!redacted.equals(s)) {
            params[i] = redacted;
            changed = true;
          }
        }
      }
      if (changed) {
        record.setParameters(params);
      }
    }
    return true;
  }

  private String redact(String input) {
    String out = input;
    if (out.indexOf("key=") >= 0) {
      out = KEY_PARAM.matcher(out).replaceAll("$1" + MASK);
    }
    for (String secret : secrets) {
      if (out.indexOf(secret) >= 0) {
        out = out.replace(secret, MASK);
      }
    }
    return out;
  }
}
