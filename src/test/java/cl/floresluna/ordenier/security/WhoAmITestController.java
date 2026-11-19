package cl.floresluna.ordenier.security;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WhoAmITestController {

	private final AuthenticatedUser authenticatedUser;

	WhoAmITestController(AuthenticatedUser authenticatedUser) {
		this.authenticatedUser = authenticatedUser;
	}

	@GetMapping("/api/v1/test/whoami")
	Map<String, Long> whoAmI() {
		return Map.of("id", authenticatedUser.id());
	}
}
