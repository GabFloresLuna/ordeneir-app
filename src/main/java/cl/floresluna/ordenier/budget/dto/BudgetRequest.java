package cl.floresluna.ordenier.budget.dto;

import cl.floresluna.ordenier.common.InvalidDataException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de guardar presupuesto. Los campos llegan como JsonNode y se validan a mano (plan 003 D6).
 * No tiene id ni userId: si vienen en el cuerpo se ignoran (R28).
 */
public record BudgetRequest(JsonNode amount, JsonNode lines) {

	static final long MIN_AMOUNT = 1L;
	static final long MAX_AMOUNT = 1_000_000_000_000L;
	static final int MAX_LINES = 100;

	public record Line(long categoryId, long amount) {
	}

	public record Validated(long amount, List<Line> lines) {
	}

	public Validated validate() {
		long general = validAmount(amount);
		return new Validated(general, validLines());
	}

	// `lines` nulo u omitido = sin líneas; si viene, debe ser un arreglo de hasta 100 objetos sin categoría repetida.
	private List<Line> validLines() {
		if (isMissing(lines)) {
			return List.of();
		}
		if (!lines.isArray() || lines.size() > MAX_LINES) {
			throw new InvalidDataException();
		}
		List<Line> result = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (JsonNode node : lines) {
			if (!node.isObject()) {
				throw new InvalidDataException();
			}
			JsonNode categoryId = node.get("categoryId");
			if (isMissing(categoryId) || !categoryId.isIntegralNumber() || !categoryId.canConvertToLong()) {
				throw new InvalidDataException();
			}
			long id = categoryId.longValue();
			long lineAmount = validAmount(node.get("amount"));
			if (!seen.add(id)) {
				throw new InvalidDataException();
			}
			result.add(new Line(id, lineAmount));
		}
		return result;
	}

	private static long validAmount(JsonNode node) {
		if (isMissing(node) || !node.isIntegralNumber() || !node.canConvertToLong()) {
			throw new InvalidDataException();
		}
		long value = node.longValue();
		if (value < MIN_AMOUNT || value > MAX_AMOUNT) {
			throw new InvalidDataException();
		}
		return value;
	}

	private static boolean isMissing(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode();
	}
}
