package cl.floresluna.ordenier.movement.dto;

import cl.floresluna.ordenier.movement.Movement;
import cl.floresluna.ordenier.movement.MovementType;
import java.time.Instant;

public record MovementResponse(Long id, long amount, MovementType type, Long categoryId, Instant occurredAt,
		String description) {

	public static MovementResponse from(Movement movement) {
		return new MovementResponse(movement.getId(), movement.getAmount(), movement.getType(),
				movement.getCategoryId(), movement.getOccurredAt(), movement.getDescription());
	}
}
