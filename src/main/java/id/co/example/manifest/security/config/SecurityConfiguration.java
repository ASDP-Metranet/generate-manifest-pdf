package id.co.example.manifest.security.config;

import id.co.example.manifest.security.basic.BasicAuthenticationProvider;
import id.co.example.manifest.security.web.RestAccessDeniedHandler;
import id.co.example.manifest.security.web.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration: stateless HTTP Basic Authentication with per-endpoint authorities.
 *
 * <p>CSRF protection is disabled because the API is stateless: no session or cookie is created and
 * every request carries its own Authorization header. The date-password pages under
 * {@code /api/v1/manifests/files/**} are public paths and do not use Basic Auth either.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BasicAuthProperties.class)
public class SecurityConfiguration {
    /** Authority required by {@code POST /api/v1/manifests/generate-pdf}. */
    public static final String MANIFEST_GENERATE = "MANIFEST_GENERATE";
    /** Authority required by {@code GET /checkmanifets} and {@code /checkmanifest}. */
    public static final String MANIFEST_CHECK = "MANIFEST_CHECK";
    /** Authority required by {@code POST /manitfestnotapprove} and {@code /manifestnotapprove}. */
    public static final String MANIFEST_APPROVAL_RESET = "MANIFEST_APPROVAL_RESET";

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    /**
     * BCrypt encoder with the configured strength.
     *
     * @param properties Basic Auth configuration
     * @return password encoder used to verify password hashes
     */
    @Bean
    public PasswordEncoder passwordEncoder(BasicAuthProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    /**
     * Builds the security filter chain.
     *
     * @param http                   security builder
     * @param properties             Basic Auth configuration
     * @param authenticationProvider provider delegating to the authentication service
     * @param entryPoint             401 handler
     * @param accessDeniedHandler    403 handler
     * @return the filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BasicAuthProperties properties,
                                                   BasicAuthenticationProvider authenticationProvider,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        if (!properties.enabled()) {
            log.warn("Basic Auth NONAKTIF (asdp.security.basic-auth.enabled=false): semua endpoint terbuka tanpa kredensial");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        String[] publicPaths = properties.publicPaths().toArray(String[]::new);
        // A dedicated manager without a parent: a failed login is checked once, not again by a global manager.
        http.authenticationManager(new ProviderManager(authenticationProvider))
                .httpBasic(basic -> basic.realmName(properties.realm()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    // Error dispatches render the status of the original request, e.g. a 404 on a public path.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/manifests/generate-pdf").hasAuthority(MANIFEST_GENERATE)
                            .requestMatchers(HttpMethod.GET, "/checkmanifets", "/checkmanifest").hasAuthority(MANIFEST_CHECK)
                            .requestMatchers(HttpMethod.POST, "/manitfestnotapprove", "/manifestnotapprove")
                            .hasAuthority(MANIFEST_APPROVAL_RESET)
                            .anyRequest().authenticated();
                });
        return http.build();
    }
}
