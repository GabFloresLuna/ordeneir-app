package cl.floresluna.ordenier.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUser {

	public long id() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
			return Long.parseLong(jwtAuthentication.getToken().getSubject());
		}
		throw new IllegalStateException("No hay un usuario autenticado con JWT");
	}
}
