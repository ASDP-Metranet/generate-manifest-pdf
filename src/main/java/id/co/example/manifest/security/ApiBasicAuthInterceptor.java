package id.co.example.manifest.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** HTTP Basic check for API callers. Credentials come from configuration, never from source code. */
@Component
public class ApiBasicAuthInterceptor implements HandlerInterceptor {
    private static final String BASIC_PREFIX = "Basic ";
    private static final String CHALLENGE = "Basic realm=\"manifest-api\", charset=\"UTF-8\"";
    private static final String UNAUTHORIZED_BODY =
            "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Kredensial tidak valid\"}";
    private final byte[] expectedUsername;
    private final byte[] expectedPassword;

    public ApiBasicAuthInterceptor(@Value("${manifest.api.username}") String username,
                                   @Value("${manifest.api.password}") String password) {
        if (username.isBlank() || password.isBlank() || username.contains(":")) {
            throw new IllegalArgumentException(
                    "manifest.api.username dan manifest.api.password wajib diisi; username tidak boleh memuat ':'");
        }
        this.expectedUsername = username.getBytes(StandardCharsets.UTF_8);
        this.expectedPassword = password.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (isAuthorized(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(UNAUTHORIZED_BODY);
        return false;
    }

    private boolean isAuthorized(String header) {
        if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return false;
        }
        String decoded;
        try {
            byte[] raw = Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim());
            decoded = new String(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return false;
        }
        byte[] username = decoded.substring(0, separator).getBytes(StandardCharsets.UTF_8);
        byte[] password = decoded.substring(separator + 1).getBytes(StandardCharsets.UTF_8);
        // Non-short-circuit so a wrong username costs the same as a wrong password.
        return MessageDigest.isEqual(expectedUsername, username) & MessageDigest.isEqual(expectedPassword, password);
    }
}
