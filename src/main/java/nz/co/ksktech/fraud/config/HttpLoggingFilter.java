package nz.co.ksktech.fraud.config;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.MDC;

/**
 * Logs every API request and response with a per-request correlation id. Mirrors the FundLens
 * {@code >>> / <<<} access-log format and adds the correlationId/MDC pattern: a UUID is generated
 * per request, pushed into the SLF4J MDC under {@code correlationId} (so it appears on every log
 * line for the request), returned to the caller as the {@code X-Correlation-Id} response header,
 * and used to tie the request line, response line, and response body together.
 *
 * <p>JSON/text bodies are logged (truncated to {@code app.logging.max-body}); binary payloads are
 * logged by media type only. Bodies are NOT redacted today (synthetic PaySim data) — see {@link
 * #redact(String)} for the clearly-marked PII redaction hook.
 */
@Provider
public class HttpLoggingFilter
    implements ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {

  /** MDC key + response header name for the correlation id. */
  public static final String CORRELATION_ID = "correlationId";

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

  private static final String START_NANOS = "fraud.request.start";

  @Inject
  @ConfigProperty(name = "app.logging.max-body", defaultValue = "2000")
  int maxBody;

  /**
   * Logs the inbound request and seeds the correlation id + start time.
   *
   * @param request the container request context
   * @throws IOException if reading the entity fails
   */
  @Override
  public void filter(ContainerRequestContext request) throws IOException {
    String correlationId = UUID.randomUUID().toString();
    MDC.put(CORRELATION_ID, correlationId);
    request.setProperty(CORRELATION_ID, correlationId);
    request.setProperty(START_NANOS, System.nanoTime());

    String detail = "";
    if (request.hasEntity() && isTextual(request.getMediaType())) {
      byte[] body = request.getEntityStream().readAllBytes();
      request.setEntityStream(new ByteArrayInputStream(body));
      detail = " body=" + truncate(redact(new String(body, StandardCharsets.UTF_8)));
    } else if (request.hasEntity()) {
      detail = " body=<" + request.getMediaType() + ">";
    }
    Log.infof(">>> %s %s%s", request.getMethod(), pathWithQuery(request), detail);
  }

  /**
   * Logs the response status + duration, echoes the correlation id header, and clears the MDC.
   *
   * @param request the container request context
   * @param response the container response context
   */
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    Object correlationId = request.getProperty(CORRELATION_ID);
    if (correlationId != null) {
      response.getHeaders().putSingle(CORRELATION_ID_HEADER, correlationId);
    }
    Object start = request.getProperty(START_NANOS);
    long millis = start instanceof Long s ? (System.nanoTime() - s) / 1_000_000 : -1;
    Log.infof(
        "<<< %s %s -> %d (%d ms)",
        request.getMethod(), pathWithQuery(request), response.getStatus(), millis);
    // MDC is removed in aroundWriteTo after the body is logged; clear here too in case
    // there is no entity body to write (e.g. 204 / error without payload).
    if (!response.hasEntity()) {
      MDC.remove(CORRELATION_ID);
    }
  }

  /**
   * Buffers and logs the textual response body, then clears the correlation id from the MDC.
   *
   * @param context the writer interceptor context
   * @throws IOException if writing to the stream fails
   * @throws WebApplicationException if a web application error occurs
   */
  @Override
  public void aroundWriteTo(WriterInterceptorContext context)
      throws IOException, WebApplicationException {
    if (!isTextual(context.getMediaType())) {
      try {
        context.proceed();
      } finally {
        MDC.remove(CORRELATION_ID);
      }
      return;
    }
    OutputStream original = context.getOutputStream();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    context.setOutputStream(buffer);
    try {
      context.proceed();
    } finally {
      byte[] body = buffer.toByteArray();
      original.write(body);
      context.setOutputStream(original);
      Log.infof("<<< response body=%s", truncate(redact(new String(body, StandardCharsets.UTF_8))));
      MDC.remove(CORRELATION_ID);
    }
  }

  /**
   * PII redaction hook. No-op today because the corpus is synthetic PaySim data; plug field/pattern
   * masking here before pointing this service at real transactions.
   *
   * @param body the body text
   * @return the (currently unmodified) body text
   */
  private static String redact(String body) {
    // TODO(PII): redact PANs, names, account numbers here before logging real data.
    return body;
  }

  /**
   * Builds the request path including any query string.
   *
   * @param request the request context
   * @return the path and query string
   */
  private static String pathWithQuery(ContainerRequestContext request) {
    var uri = request.getUriInfo().getRequestUri();
    return uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
  }

  /**
   * Determines whether a media type carries a loggable textual body.
   *
   * @param mediaType the media type
   * @return true if textual, false otherwise
   */
  private static boolean isTextual(MediaType mediaType) {
    if (mediaType == null) {
      return false;
    }
    String subtype = mediaType.getSubtype();
    return "text".equalsIgnoreCase(mediaType.getType())
        || "json".equalsIgnoreCase(subtype)
        || subtype.endsWith("+json")
        || "x-www-form-urlencoded".equalsIgnoreCase(subtype);
  }

  /**
   * Truncates a body string to {@code app.logging.max-body} characters.
   *
   * @param body the body string
   * @return the truncated string
   */
  private String truncate(String body) {
    String single = body.strip();
    if (single.length() <= maxBody) {
      return single;
    }
    return single.substring(0, maxBody) + "…(+" + (single.length() - maxBody) + " chars)";
  }
}
