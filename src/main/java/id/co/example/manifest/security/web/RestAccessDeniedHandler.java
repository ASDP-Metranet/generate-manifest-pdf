package id.co.example.manifest.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Answers authenticated requests that lack the required authority with 403 {@code AUTH-403}. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final SecurityErrorResponseWriter writer;

    /**
     * Creates the handler.
     *
     * @param writer error body writer
     */
    public RestAccessDeniedHandler(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    /**
     * Logs the denial and writes the 403 response.
     *
     * @param request               current request
     * @param response              current response
     * @param accessDeniedException reason access was denied
     * @throws IOException when the body cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        String user = request.getUserPrincipal() == null ? "-" : request.getUserPrincipal().getName();
        log.warn("Access denied user={} method={} path={}", user, request.getMethod(), request.getRequestURI());
        writer.write(request, response, AuthErrorCode.FORBIDDEN);
    }
}
