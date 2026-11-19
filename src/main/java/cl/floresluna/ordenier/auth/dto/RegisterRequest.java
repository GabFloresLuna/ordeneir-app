package cl.floresluna.ordenier.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotNull @Size(min = 8, max = 64) String password) {

	// BCrypt ignora lo que pase del byte 72 (plan, límites fijados).
	private static final int MAX_PASSWORD_BYTES = 72;

	public RegisterRequest {
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}

	@AssertTrue
	public boolean isPasswordWithinByteLimit() {
		return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
	}
}
