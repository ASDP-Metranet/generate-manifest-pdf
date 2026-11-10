package id.co.example.manifest.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/** Writes the standard {@link ErrorResponse} JSON for authentication and authorization failures. */
@Component
public class SecurityErrorResponseWriter {
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Creates the writer with the system UTC clock.
     *
     * @param jsonMapper JSON serializer
     */
    @Autowired
    public SecurityErrorResponseWriter(JsonMapper jsonMapper) {
        this(jsonMapper, Clock.systemUTC());
    }

    SecurityErrorResponseWriter(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * Sets the status and writes the error body. The body never contains credentials.
     *
     * @param request  current request
     * @param response current response
     * @param error    error to report
     * @throws IOException when the body cannot be written
     */
    public void write(HttpServletRequest request, HttpServletResponse response, AuthErrorCode error)
            throws IOException {
        ErrorResponse body = new ErrorResponse(error.code(), error.message(), Instant.now(clock).toString(),
                request.getRequestURI(), CorrelationIdFilter.current(request));
        response.setStatus(error.status().value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
