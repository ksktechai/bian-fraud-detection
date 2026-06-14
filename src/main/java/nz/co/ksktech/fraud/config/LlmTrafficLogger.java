package nz.co.ksktech.fraud.config;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Logs LLM prompts and responses at the model layer via a langchain4j {@link ChatModelListener},
 * for both the Gemini and Ollama providers. This is the SAFE way to see request/response traffic:
 * unlike the providers' built-in HTTP request loggers (which print the full request URL — and the
 * Gemini client puts the API key in that URL's {@code ?key=} parameter), a listener only sees the
 * chat messages and the model's answer, never the URL or the key.
 *
 * <p>Enabled with {@code LOG_LLM=true} (app.llm.log-traffic). Quarkus auto-registers
 * {@code ChatModelListener} CDI beans with every chat model.
 */
@ApplicationScoped
public class LlmTrafficLogger implements ChatModelListener {

  private static final int MAX = 4000;

  private final boolean enabled;

  /**
   * Constructor.
   *
   * @param enabled whether LLM traffic logging is on (app.llm.log-traffic / LOG_LLM)
   */
  public LlmTrafficLogger(
      @ConfigProperty(name = "app.llm.log-traffic", defaultValue = "false") boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public void onRequest(ChatModelRequestContext ctx) {
    if (!enabled) {
      return;
    }
    Log.infof(
        "LLM >>> provider=%s request=%s",
        ctx.modelProvider(), truncate(String.valueOf(ctx.chatRequest().messages())));
  }

  @Override
  public void onResponse(ChatModelResponseContext ctx) {
    if (!enabled) {
      return;
    }
    var ai = ctx.chatResponse().aiMessage();
    Log.infof("LLM <<< response=%s", truncate(ai == null ? "<none>" : ai.text()));
  }

  @Override
  public void onError(ChatModelErrorContext ctx) {
    // Always log errors (rate limit / 503 / timeout) regardless of the traffic flag.
    Log.warnf("LLM error: %s", ctx.error() == null ? "unknown" : ctx.error().toString());
  }

  private static String truncate(String s) {
    if (s == null) {
      return "<null>";
    }
    return s.length() <= MAX ? s : s.substring(0, MAX) + "…(+" + (s.length() - MAX) + " chars)";
  }
}
