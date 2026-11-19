package cl.floresluna.ordenier.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;

// Solo valida que los campos existan (plan D4): sin límites de largo, para no revelar reglas.
public record LoginRequest(@NotBlank String email, @NotNull String password) {

	public LoginRequest {
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}
}
