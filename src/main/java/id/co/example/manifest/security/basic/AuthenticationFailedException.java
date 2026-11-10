package id.co.example.manifest.security.basic;

/**
 * Base class for authentication failures raised by {@link AuthenticationService}.
 * Messages are generic so they never reveal whether a username exists.
 */
public abstract class AuthenticationFailedException extends RuntimeException {
    /**
     * Creates a failure with a generic, client-safe message.
     *
     * @param message message that may be shown to the client
     */
    protected AuthenticationFailedException(String message) {
        super(message);
    }
}
