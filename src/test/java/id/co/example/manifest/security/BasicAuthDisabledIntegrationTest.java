package id.co.example.manifest.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static id.co.example.manifest.security.BasicAuthTestSupport.generatePdf;
import static id.co.example.manifest.security.BasicAuthTestSupport.send;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** With asdp.security.basic-auth.enabled=false the protected endpoints open without credentials. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "asdp.security.basic-auth.enabled=false")
class BasicAuthDisabledIntegrationTest {
    @Value("${local.server.port}") int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        BasicAuthTestSupport.register(registry);
    }

    @Test
    void protectedEndpointNeedsNoCredentialsWhenDisabled() {
        assertEquals(201, send(generatePdf(port, null)).statusCode());
    }
}
