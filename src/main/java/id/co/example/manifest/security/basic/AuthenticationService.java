package id.co.example.manifest.security.basic;

/** Domain contract for verifying a username and password. */
public interface AuthenticationService {
    /**
     * Verifies the credentials and returns the authenticated user.
     *
     * @param username login name sent by the client
     * @param password raw password sent by the client; never stored or logged
     * @return the authenticated user with its authorities
     * @throws InvalidCredentialException when credentials are missing, unknown or wrong
     * @throws UserDisabledException      when the password is correct but the account is disabled
     */
    AuthenticatedUser authenticate(String username, String password);
}
