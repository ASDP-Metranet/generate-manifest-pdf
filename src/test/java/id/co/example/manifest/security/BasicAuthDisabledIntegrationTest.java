package id.co.example.manifest.security;

import id.co.example.manifest.repository.GeneratedPdfManifestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static id.co.example.manifest.security.BasicAuthTestSupport.generatePdf;
import static id.co.example.manifest.security.BasicAuthTestSupport.send;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** With asdp.security.basic-auth.enabled=false the protected endpoints open without credentials. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "asdp.security.basic-auth.enabled=false")
class BasicAuthDisabledIntegrationTest {
    @Value("${local.server.port}") int port;
    @MockitoBean GeneratedPdfManifestRepository generatedManifests;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        BasicAuthTestSupport.register(registry);
    }

    @Test
    void protectedEndpointNeedsNoCredentialsWhenDisabled() {
        assertEquals(201, send(generatePdf(port, null)).statusCode());
    }
}
