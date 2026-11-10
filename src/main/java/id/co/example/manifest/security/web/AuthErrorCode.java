package id.co.example.manifest.security.web;

import org.springframework.http.HttpStatus;

/** Standard authentication and authorization error codes with their HTTP status and client message. */
public enum AuthErrorCode {
    /** Credentials missing or invalid. */
    UNAUTHENTICATED("AUTH-401", HttpStatus.UNAUTHORIZED, "Kredensial tidak valid"),
    /** Authenticated user lacks the required permission. */
    FORBIDDEN("AUTH-403", HttpStatus.FORBIDDEN, "Akses ditolak"),
    /** Correct password but the account is disabled. */
    USER_DISABLED("AUTH-USER-DISABLED", HttpStatus.UNAUTHORIZED, "Akun tidak aktif"),
    /** Unexpected failure while authenticating; details are only in the server log. */
    INTERNAL_ERROR("AUTH-INTERNAL-ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan internal");

    private final String code;
    private final HttpStatus status;
    private final String message;

    AuthErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    /**
     * Returns the code sent in the {@code code} field.
     *
     * @return error code such as {@code AUTH-401}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the HTTP status for this error.
     *
     * @return HTTP status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Returns the client-safe message.
     *
     * @return message without sensitive details
     */
    public String message() {
        return message;
    }
}
