package id.co.example.manifest.security.web;

import id.co.example.manifest.security.config.BasicAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers requests that are not authenticated: 401 with {@code AUTH-401} or {@code AUTH-USER-DISABLED},
 * or 500 with {@code AUTH-INTERNAL-ERROR} when authentication itself failed.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final SecurityErrorResponseWriter writer;
    private final String challenge;

    /**
     * Creates the entry point.
     *
     * @param writer     error body writer
     * @param properties Basic Auth configuration providing the realm
     */
    public RestAuthenticationEntryPoint(SecurityErrorResponseWriter writer, BasicAuthProperties properties) {
        this.writer = writer;
        this.challenge = "Basic realm=\"" + properties.realm().replace("\"", "") + "\", charset=\"UTF-8\"";
    }

    /**
     * Writes the error response for an unauthenticated request.
     *
     * @param request       current request
     * @param response      current response
     * @param authException reason authentication failed or is missing
     * @throws IOException when the body cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (authException instanceof AuthenticationServiceException) {
            log.error("Authentication internal error path={}", request.getRequestURI(), authException);
            writer.write(request, response, AuthErrorCode.INTERNAL_ERROR);
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        writer.write(request, response, authException instanceof DisabledException
                ? AuthErrorCode.USER_DISABLED
                : AuthErrorCode.UNAUTHENTICATED);
    }
}
