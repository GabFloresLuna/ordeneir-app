package cl.floresluna.ordenier.category.dto;

import cl.floresluna.ordenier.common.InvalidDataException;
import cl.floresluna.ordenier.movement.MovementType;
import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de crear categoría. Los campos llegan como JsonNode y se validan a mano (plan D6).
 * No tiene id, userId ni predefined: si vienen en el cuerpo se ignoran (R28).
 */
public record CategoryRequest(JsonNode name, JsonNode type) {

	static final int MAX_NAME = 50;

	public record Validated(String name, MovementType type) {
	}

	public Validated validate() {
		return new Validated(validName(), validType());
	}

	// Texto de 1 a 50 caracteres tras recortar; se cuenta en code points, no en unidades UTF-16 (plan).
	/** Solo el nombre: al editar, un `type` en el cuerpo se ignora (R3, plan). */
	public String validateName() {
		return validName();
	}

	private String validName() {
		if (isMissing(name) || !name.isString()) {
			throw new InvalidDataException();
		}
		String trimmed = name.stringValue().strip();
		if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > MAX_NAME) {
			throw new InvalidDataException();
		}
		return trimmed;
	}

	private MovementType validType() {
		if (isMissing(type) || !type.isString()) {
			throw new InvalidDataException();
		}
		return parseType(type.stringValue());
	}

	/** Tipo exacto `INGRESO` o `GASTO`; cualquier otro valor es inválido (R5, R7). */
	public static MovementType parseType(String text) {
		for (MovementType candidate : MovementType.values()) {
			if (candidate.name().equals(text)) {
				return candidate;
			}
		}
		throw new InvalidDataException();
	}

	private static boolean isMissing(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode();
	}
}
