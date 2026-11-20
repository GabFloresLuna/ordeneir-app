package cl.floresluna.ordenier.category.dto;

import cl.floresluna.ordenier.category.Category;
import cl.floresluna.ordenier.movement.MovementType;

public record CategoryResponse(Long id, String name, MovementType type, boolean predefined) {

	public static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getType(),
				category.getUserId() == null);
	}
}
