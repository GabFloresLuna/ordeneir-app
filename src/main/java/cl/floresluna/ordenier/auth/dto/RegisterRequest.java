package cl.floresluna.ordenier.auth.dto;

import java.util.Locale;

public record RegisterRequest(String email, String password) {

	public RegisterRequest {
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}
}
