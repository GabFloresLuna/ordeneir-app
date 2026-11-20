package cl.floresluna.ordenier.movement;

import cl.floresluna.ordenier.common.InvalidDataException;
import cl.floresluna.ordenier.common.PageResponse;
import cl.floresluna.ordenier.movement.dto.MovementRequest;
import cl.floresluna.ordenier.movement.dto.MovementResponse;
import cl.floresluna.ordenier.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movimientos")
public class MovementController {

	private static final int MAX_SIZE = 100;

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

	@GetMapping
	public PageResponse<MovementResponse> list(@RequestParam(required = false) String mes,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		if (page < 0 || size < 1 || size > MAX_SIZE) {
			throw new InvalidDataException();
		}
		return movementService.list(authenticatedUser.id(), mes, page, size);
	}

	@GetMapping("/{id}")
	public MovementResponse get(@PathVariable long id) {
		return movementService.get(authenticatedUser.id(), id);
	}

	@PutMapping("/{id}")
	public MovementResponse update(@PathVariable long id, @RequestBody MovementRequest request) {
		return movementService.update(authenticatedUser.id(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable long id) {
		movementService.delete(authenticatedUser.id(), id);
	}
}
