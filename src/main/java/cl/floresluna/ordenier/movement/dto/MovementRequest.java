package cl.floresluna.ordenier.movement.dto;

import cl.floresluna.ordenier.common.InvalidDataException;
import cl.floresluna.ordenier.movement.MovementType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de crear/editar. Los campos llegan como JsonNode y se validan a mano
 * para que la respuesta no dependa de la coerción de Jackson (plan D2, D3, D4).
 * No tiene id ni userId: si vienen en el cuerpo se ignoran (plan D5).
 */
public record MovementRequest(JsonNode amount, JsonNode type, JsonNode categoryId, JsonNode occurredAt,
		JsonNode description) {

	static final long MIN_AMOUNT = 1L;
	static final long MAX_AMOUNT = 1_000_000_000_000L;
	static final int MAX_DESCRIPTION = 200;
	static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);

	public record Validated(long amount, MovementType type, long categoryId, Instant occurredAt,
			String description) {
	}

	public Validated validate(Clock clock) {
		return new Validated(validAmount(), validType(), validCategoryId(), validOccurredAt(clock),
				validDescription());
	}

	private long validAmount() {
		if (isMissing(amount) || !amount.isIntegralNumber() || !amount.canConvertToLong()) {
			throw new InvalidDataException();
		}
		long value = amount.longValue();
		if (value < MIN_AMOUNT || value > MAX_AMOUNT) {
			throw new InvalidDataException();
		}
		return value;
	}

	private MovementType validType() {
		if (isMissing(type) || !type.isString()) {
			throw new InvalidDataException();
		}
		String text = type.stringValue();
		for (MovementType candidate : MovementType.values()) {
			if (candidate.name().equals(text)) {
				return candidate;
			}
		}
		throw new InvalidDataException();
	}

	// Debe venir como entero (R14); si existe y es válida lo decide el servicio (R15, plan D7).
	private long validCategoryId() {
		if (isMissing(categoryId) || !categoryId.isIntegralNumber() || !categoryId.canConvertToLong()) {
			throw new InvalidDataException();
		}
		return categoryId.longValue();
	}

	private Instant validOccurredAt(Clock clock) {
		if (isMissing(occurredAt) || !occurredAt.isString()) {
			throw new InvalidDataException();
		}
		Instant instant;
		try {
			instant = OffsetDateTime.parse(occurredAt.stringValue()).toInstant();
		} catch (DateTimeParseException e) {
			throw new InvalidDataException();
		}
		if (instant.isAfter(clock.instant().plus(CLOCK_TOLERANCE))) {
			throw new InvalidDataException();
		}
		return instant;
	}

	private String validDescription() {
		if (isMissing(description)) {
			return null;
		}
		if (!description.isString()) {
			throw new InvalidDataException();
		}
		String trimmed = description.stringValue().trim();
		if (trimmed.length() > MAX_DESCRIPTION) {
			throw new InvalidDataException();
		}
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static boolean isMissing(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode();
	}
}
