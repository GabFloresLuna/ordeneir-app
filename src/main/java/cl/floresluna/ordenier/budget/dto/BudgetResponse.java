package cl.floresluna.ordenier.budget.dto;

import java.util.List;

/** Presupuesto completo: general y por categoría, con gastado, restante y excedido (R21). */
public record BudgetResponse(String month, long amount, long spent, long remaining, boolean exceeded,
		List<Line> lines) {

	public record Line(long categoryId, long amount, long spent, long remaining, boolean exceeded) {
	}
}
