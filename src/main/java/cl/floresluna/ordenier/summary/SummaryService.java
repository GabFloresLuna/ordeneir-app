package cl.floresluna.ordenier.summary;

import cl.floresluna.ordenier.category.Category;
import cl.floresluna.ordenier.category.CategoryRepository;
import cl.floresluna.ordenier.common.MonthRange;
import cl.floresluna.ordenier.movement.CategorySpent;
import cl.floresluna.ordenier.movement.MovementRepository;
import cl.floresluna.ordenier.movement.MovementType;
import cl.floresluna.ordenier.summary.dto.SummaryResponse;
import cl.floresluna.ordenier.summary.dto.SummaryResponse.CategoryTotal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

	private final MovementRepository movementRepository;
	private final CategoryRepository categoryRepository;

	public SummaryService(MovementRepository movementRepository, CategoryRepository categoryRepository) {
		this.movementRepository = movementRepository;
		this.categoryRepository = categoryRepository;
	}

	// El mes se valida antes de consultar (plan 004 D2). El dueño viene del JWT (R10). Un mes sin movimientos
	// o futuro no es un caso especial: las consultas devuelven vacío y salen ceros (R6, D8).
	// T2: desglose por categoría; el presupuesto sale nulo hasta T3.
	@Transactional(readOnly = true)
	public SummaryResponse get(long userId, String month) {
		MonthRange range = MonthRange.parse(month);
		List<CategorySpent> expenses = movementRepository.sumAmountByCategory(userId, MovementType.GASTO,
				range.from(), range.to());
		List<CategorySpent> income = movementRepository.sumAmountByCategory(userId, MovementType.INGRESO,
				range.from(), range.to());
		Map<Long, String> names = categoryNames(expenses, income);
		List<CategoryTotal> expensesByCategory = toCategoryTotals(expenses, names);
		List<CategoryTotal> incomeByCategory = toCategoryTotals(income, names);
		// Cada total es la suma de su lista (plan 004 D3, R3).
		long totalExpenses = sum(expensesByCategory);
		long totalIncome = sum(incomeByCategory);
		return new SummaryResponse(month, totalIncome, totalExpenses, totalIncome - totalExpenses,
				expensesByCategory, incomeByCategory, null);
	}

	// Una sola consulta para los nombres (plan 004 D5). Una categoría ausente es un error interno, no un dato inventado.
	private Map<Long, String> categoryNames(List<CategorySpent> expenses, List<CategorySpent> income) {
		Set<Long> ids = new HashSet<>();
		expenses.forEach(row -> ids.add(row.categoryId()));
		income.forEach(row -> ids.add(row.categoryId()));
		if (ids.isEmpty()) {
			return Map.of();
		}
		return categoryRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Category::getId, Category::getName));
	}

	// Orden: total descendente, nombre sin distinguir mayúsculas, id (plan 004 D6).
	private List<CategoryTotal> toCategoryTotals(List<CategorySpent> rows, Map<Long, String> names) {
		return rows.stream()
				.map(row -> new CategoryTotal(row.categoryId(), nameOf(names, row.categoryId()), row.total()))
				.sorted(Comparator.comparingLong(CategoryTotal::total).reversed()
						.thenComparing(total -> total.name().toLowerCase())
						.thenComparingLong(CategoryTotal::categoryId))
				.toList();
	}

	private String nameOf(Map<Long, String> names, long categoryId) {
		String name = names.get(categoryId);
		if (name == null) {
			throw new IllegalStateException("Categoría " + categoryId + " de un movimiento no existe");
		}
		return name;
	}

	private long sum(List<CategoryTotal> totals) {
		return totals.stream().mapToLong(CategoryTotal::total).sum();
	}
}
