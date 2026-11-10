package id.co.example.manifest.security.basic;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bridges Spring Security's Basic Auth filter to {@link AuthenticationService} and translates
 * domain failures into Spring Security exceptions. The returned token carries no credentials.
 */
@Component
public class BasicAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationService authenticationService;

    /**
     * Creates the provider.
     *
     * @param authenticationService domain authentication logic
     */
    public BasicAuthenticationProvider(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Authenticates the username and password decoded from the Authorization header.
     *
     * @param authentication unauthenticated username/password token
     * @return an authenticated token with the user's authorities and no credentials
     * @throws AuthenticationException when authentication fails
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        try {
            AuthenticatedUser user = authenticationService.authenticate(
                    username, credentials == null ? null : credentials.toString());
            List<SimpleGrantedAuthority> authorities = user.authorities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            return UsernamePasswordAuthenticationToken.authenticated(user.username(), null, authorities);
        } catch (UserDisabledException ex) {
            throw new DisabledException(ex.getMessage());
        } catch (InvalidCredentialException ex) {
            throw new BadCredentialsException(ex.getMessage());
        } catch (RuntimeException ex) {
            throw new AuthenticationServiceException("Kegagalan internal autentikasi", ex);
        }
    }

    /**
     * Handles username/password tokens only.
     *
     * @param authentication token type
     * @return {@code true} for {@link UsernamePasswordAuthenticationToken}
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
