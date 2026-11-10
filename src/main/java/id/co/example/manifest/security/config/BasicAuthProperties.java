package id.co.example.manifest.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * External configuration for HTTP Basic Authentication, bound from {@code asdp.security.basic-auth.*}.
 * Sensitive values (usernames, password hashes) must come from environment variables or a secret manager.
 *
 * @param enabled        whether Basic Auth protects the API; {@code false} opens every endpoint
 * @param realm          realm sent in the {@code WWW-Authenticate} header
 * @param publicPaths    path patterns reachable without credentials
 * @param bcryptStrength BCrypt cost used when this application encodes a password
 * @param users          accounts allowed to authenticate
 */
@Validated
@ConfigurationProperties(prefix = "asdp.security.basic-auth")
public record BasicAuthProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank String realm,
        @DefaultValue List<@NotBlank String> publicPaths,
        @DefaultValue("12") @Min(10) @Max(16) int bcryptStrength,
        @DefaultValue List<@NotNull @Valid User> users
) {
    /** Accepts BCrypt hashes only ($2a$, $2b$ or $2y$), so a plaintext password fails at startup. */
    public static final String BCRYPT_PATTERN = "^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$";

    /**
     * Fails startup when Basic Auth is enabled but no account is configured.
     *
     * @return {@code true} when the configuration is usable
     */
    @AssertTrue(message = "asdp.security.basic-auth.users wajib diisi jika Basic Auth aktif")
    public boolean isUsersConfiguredWhenEnabled() {
        return !enabled || !users.isEmpty();
    }

    /**
     * One account allowed to call the API.
     *
     * @param username     login name; must not contain ':' or whitespace
     * @param passwordHash BCrypt hash of the password, never the password itself
     * @param enabled      {@code false} rejects the account even with a correct password
     * @param authorities  permissions granted to the account; none by default (least privilege)
     */
    public record User(
            @NotBlank @Pattern(regexp = "[^:\\s]{1,100}", message = "tidak boleh memuat ':' atau spasi") String username,
            @NotBlank @Pattern(regexp = BCRYPT_PATTERN, message = "harus hash BCrypt, bukan password asli") String passwordHash,
            @DefaultValue("true") boolean enabled,
            @DefaultValue List<@NotBlank String> authorities
    ) {
        /**
         * Hides the password hash so the account can be logged safely.
         *
         * @return a description without the hash
         */
        @Override
        public String toString() {
            return "User[username=" + username + ", enabled=" + enabled + ", authorities=" + authorities + "]";
        }
    }
}
