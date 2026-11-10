package id.co.example.manifest.security.web;

/**
 * Standard error body for authentication and authorization failures.
 *
 * @param code          error code such as {@code AUTH-401}
 * @param message       client-safe message
 * @param timestamp     ISO-8601 time of the error
 * @param path          request path
 * @param correlationId ID shared with the server log for this request
 */
public record ErrorResponse(String code, String message, String timestamp, String path, String correlationId) {
}
