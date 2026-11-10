package id.co.example.manifest.security.basic;

/**
 * The password matched but the account is disabled. Raised only after a correct password,
 * so it cannot be used to discover which usernames exist.
 */
public class UserDisabledException extends AuthenticationFailedException {
    /** Creates the exception with the message "Akun tidak aktif". */
    public UserDisabledException() {
        super("Akun tidak aktif");
    }
}
