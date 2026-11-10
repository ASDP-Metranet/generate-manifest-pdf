package id.co.example.manifest.security.basic;

/** Credentials are missing, the username is unknown, or the password does not match. */
public class InvalidCredentialException extends AuthenticationFailedException {
    /** Creates the exception with the generic message "Kredensial tidak valid". */
    public InvalidCredentialException() {
        super("Kredensial tidak valid");
    }
}
