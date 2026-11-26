package cl.floresluna.ordenier.summary.dto;

import cl.floresluna.ordenier.budget.dto.BudgetResponse;
import java.util.List;

/**
 * Resumen del mes (R1): totales, balance, desglose por categoría y presupuesto (nulo si no hay).
 * Montos enteros en CLP (AGENTS.md, reglas técnicas).
 */
public record SummaryResponse(String month, long totalIncome, long totalExpenses, long balance,
		List<CategoryTotal> expensesByCategory, List<CategoryTotal> incomeByCategory, BudgetResponse budget) {

	public record CategoryTotal(long categoryId, String name, long total) {
	}
}
