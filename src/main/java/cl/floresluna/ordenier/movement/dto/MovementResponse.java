package cl.floresluna.ordenier.movement.dto;

import cl.floresluna.ordenier.movement.Movement;
import cl.floresluna.ordenier.movement.MovementType;
import java.time.Instant;

public record MovementResponse(Long id, long amount, MovementType type, Long categoryId, Instant occurredAt,
		String description, BudgetAlert budget) {

	// Ver y listar no calculan el aviso: `budget` es nulo (R26, plan 003 D12).
	public static MovementResponse from(Movement movement) {
		return from(movement, null);
	}

	public static MovementResponse from(Movement movement, BudgetAlert budget) {
		return new MovementResponse(movement.getId(), movement.getAmount(), movement.getType(),
				movement.getCategoryId(), movement.getOccurredAt(), movement.getDescription(), budget);
	}
}
