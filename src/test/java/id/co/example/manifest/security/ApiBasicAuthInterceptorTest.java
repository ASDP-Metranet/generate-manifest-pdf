package id.co.example.manifest.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ApiBasicAuthInterceptorTest {
    private final ApiBasicAuthInterceptor interceptor = new ApiBasicAuthInterceptor("user", "pa:ss");

    @Test
    void acceptsCorrectCredentials() throws IOException {
        assertTrue(call(basic("user:pa:ss")).allowed());
        assertTrue(call("basic " + encode("user:pa:ss")).allowed());
    }

    @Test
    void rejectsMissingOrInvalidCredentials() throws IOException {
        for (String header : new String[]{null, "", "Bearer abc", "Basic", "Basic !!!not-base64!!!",
                basic("usernocolon"), basic("user:wrong"), basic("wrong:pa:ss"), basic("user:"), basic(":pa:ss")}) {
            Result result = call(header);
            assertFalse(result.allowed(), "header: " + header);
            assertEquals(401, result.response().getStatus());
            assertTrue(result.response().getHeader(HttpHeaders.WWW_AUTHENTICATE).startsWith("Basic "));
            assertEquals("no-store", result.response().getHeader(HttpHeaders.CACHE_CONTROL));
        }
    }

    @Test
    void refusesToStartWithoutUsableCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new ApiBasicAuthInterceptor("", "secret"));
        assertThrows(IllegalArgumentException.class, () -> new ApiBasicAuthInterceptor("user", " "));
        assertThrows(IllegalArgumentException.class, () -> new ApiBasicAuthInterceptor("us:er", "secret"));
    }

    private Result call(String header) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/manifests/generate-pdf");
        if (header != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        return new Result(interceptor.preHandle(request, response, new Object()), response);
    }

    private static String basic(String credentials) {
        return "Basic " + encode(credentials);
    }

    private static String encode(String credentials) {
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private record Result(boolean allowed, MockHttpServletResponse response) {}
}
