package id.co.example.manifest.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Interceptor rather than servlet filter: it matches with the same path rules as the controller,
 * so URI variants such as ";param" or percent-encoding cannot reach the handler unchecked.
 * The /files/** endpoints stay outside it because browsers use the date password form there.
 */
@Configuration
public class ApiSecurityConfig implements WebMvcConfigurer {
    private final ApiBasicAuthInterceptor basicAuth;

    public ApiSecurityConfig(ApiBasicAuthInterceptor basicAuth) {
        this.basicAuth = basicAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(basicAuth)
                .addPathPatterns("/api/v1/manifests/generate-pdf", "/checkmanifets", "/checkmanifest");
    }
}
