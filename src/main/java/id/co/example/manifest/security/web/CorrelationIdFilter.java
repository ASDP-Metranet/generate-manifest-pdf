package id.co.example.manifest.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns each request a correlation ID, taken from {@code X-Correlation-Id} when it is safe or
 * generated otherwise. The ID is put in the log MDC, the response header and the error body.
 * Runs before Spring Security so authentication logs carry the ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    /** Request and response header carrying the correlation ID. */
    public static final String HEADER = "X-Correlation-Id";
    /** MDC key used in the log pattern. */
    public static final String MDC_KEY = "correlationId";
    private static final String ATTRIBUTE = CorrelationIdFilter.class.getName();
    // Restricting the client value prevents log injection through the header.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * Returns the correlation ID of the request, also during error dispatch.
     *
     * @param request current request
     * @return the correlation ID, or {@code null} when the filter did not run
     */
    public static String current(HttpServletRequest request) {
        Object id = request.getAttribute(ATTRIBUTE);
        return id == null ? MDC.get(MDC_KEY) : id.toString();
    }

    /**
     * Sets the correlation ID for the duration of the request.
     *
     * @param request  current request
     * @param response current response
     * @param chain    remaining filter chain
     * @throws ServletException when a later filter fails
     * @throws IOException      when a later filter fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
