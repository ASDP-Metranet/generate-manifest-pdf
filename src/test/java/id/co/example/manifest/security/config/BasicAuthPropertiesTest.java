package id.co.example.manifest.security.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAuthPropertiesTest {
    private static final String HASH = new BCryptPasswordEncoder(4).encode("secret");

    @Test
    void acceptsBcryptHash() {
        assertTrue(validate(properties(true, user("api-user", HASH))));
    }

    @Test
    void rejectsPlaintextPassword() {
        assertFalse(validate(properties(true, user("api-user", "plain-text-password"))));
    }

    @Test
    void rejectsUsernameWithColon() {
        assertFalse(validate(properties(true, user("api:user", HASH))));
    }

    @Test
    void requiresUsersOnlyWhenEnabled() {
        assertFalse(validate(new BasicAuthProperties(true, "realm", List.of(), 12, List.of())));
        assertTrue(validate(new BasicAuthProperties(false, "realm", List.of(), 12, List.of())));
    }

    @Test
    void toStringNeverContainsPasswordHash() {
        assertFalse(user("api-user", HASH).toString().contains(HASH));
    }

    private static boolean validate(BasicAuthProperties properties) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(properties).isEmpty();
        }
    }

    private static BasicAuthProperties properties(boolean enabled, BasicAuthProperties.User user) {
        return new BasicAuthProperties(enabled, "asdp-internal-api", List.of(), 12, List.of(user));
    }

    private static BasicAuthProperties.User user(String username, String hash) {
        return new BasicAuthProperties.User(username, hash, true, List.of("MANIFEST_CHECK"));
    }
}
