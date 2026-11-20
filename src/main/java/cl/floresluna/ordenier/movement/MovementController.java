package cl.floresluna.ordenier.movement;

import cl.floresluna.ordenier.movement.dto.MovementRequest;
import cl.floresluna.ordenier.movement.dto.MovementResponse;
import cl.floresluna.ordenier.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movimientos")
public class MovementController {

	private final MovementService movementService;
	private final AuthenticatedUser authenticatedUser;

	public MovementController(MovementService movementService, AuthenticatedUser authenticatedUser) {
		this.movementService = movementService;
		this.authenticatedUser = authenticatedUser;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MovementResponse create(@RequestBody MovementRequest request) {
		return movementService.create(authenticatedUser.id(), request);
	}

	@GetMapping("/{id}")
	public MovementResponse get(@PathVariable long id) {
		return movementService.get(authenticatedUser.id(), id);
	}
}
