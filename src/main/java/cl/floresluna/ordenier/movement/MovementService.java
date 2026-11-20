package cl.floresluna.ordenier.movement;

import cl.floresluna.ordenier.common.MovementNotFoundException;
import cl.floresluna.ordenier.movement.dto.MovementRequest;
import cl.floresluna.ordenier.movement.dto.MovementResponse;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class MovementService {

	private final MovementRepository movementRepository;
	private final Clock clock;

	public MovementService(MovementRepository movementRepository, Clock clock) {
		this.movementRepository = movementRepository;
		this.clock = clock;
	}

	// El dueño viene siempre del JWT, nunca del cuerpo (R19, plan D5).
	public MovementResponse create(long userId, MovementRequest request) {
		MovementRequest.Validated data = request.validate(clock);
		Movement saved = movementRepository.save(new Movement(userId, data.amount(), data.type(),
				data.occurredAt(), data.description()));
		return MovementResponse.from(saved);
	}

	// Inexistente, eliminado o ajeno dan la misma respuesta (R12, plan D6).
	public MovementResponse get(long userId, long id) {
		return movementRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
				.map(MovementResponse::from)
				.orElseThrow(MovementNotFoundException::new);
	}
}
