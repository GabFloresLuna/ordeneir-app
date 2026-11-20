package cl.floresluna.ordenier.budget;

import cl.floresluna.ordenier.budget.dto.BudgetRequest;
import cl.floresluna.ordenier.budget.dto.BudgetResponse;
import cl.floresluna.ordenier.category.CategoryRepository;
import cl.floresluna.ordenier.common.BudgetNotFoundException;
import cl.floresluna.ordenier.common.BudgetSumExceedsException;
import cl.floresluna.ordenier.common.InvalidCategoryException;
import cl.floresluna.ordenier.common.MonthRange;
import cl.floresluna.ordenier.movement.CategorySpent;
import cl.floresluna.ordenier.movement.MovementRepository;
import cl.floresluna.ordenier.movement.MovementType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

	private final BudgetRepository budgetRepository;
	private final BudgetLineRepository budgetLineRepository;
	private final CategoryRepository categoryRepository;
	private final MovementRepository movementRepository;

	public BudgetService(BudgetRepository budgetRepository, BudgetLineRepository budgetLineRepository,
			CategoryRepository categoryRepository, MovementRepository movementRepository) {
		this.budgetRepository = budgetRepository;
		this.budgetLineRepository = budgetLineRepository;
		this.categoryRepository = categoryRepository;
		this.movementRepository = movementRepository;
	}

	// Orden de comprobación: mes → cuerpo → categorías → suma (plan 003 D6). El dueño viene del JWT (R28).
	// Reemplaza el presupuesto del mes: actualiza el general, borra sus líneas con consulta masiva y inserta
	// las nuevas (plan D8). Si algo falla, la transacción no guarda nada (R19, R20).
	@Transactional
	public BudgetResponse put(long userId, String month, BudgetRequest request) {
		MonthRange range = MonthRange.parse(month);
		BudgetRequest.Validated data = request.validate();
		for (BudgetRequest.Line line : data.lines()) {
			requireExpenseCategory(userId, line.categoryId());
		}
		long sum = data.lines().stream().mapToLong(BudgetRequest.Line::amount).sum();
		if (sum > data.amount()) {
			throw new BudgetSumExceedsException();
		}

		Budget budget = budgetRepository.findByUserIdAndMonth(userId, month).orElse(null);
		if (budget == null) {
			budget = budgetRepository.save(new Budget(userId, month, data.amount()));
		} else {
			budget.changeAmount(data.amount());
			budgetLineRepository.deleteByBudgetId(budget.getId());
		}
		for (BudgetRequest.Line line : data.lines()) {
			budgetLineRepository.save(new BudgetLine(budget.getId(), line.categoryId(), line.amount()));
		}
		return toResponse(userId, month, range, budget.getAmount(), budget.getId());
	}

	// El mes se valida antes de buscar (plan 003 D11); sin presupuesto del usuario para el mes, 404 (R22).
	@Transactional(readOnly = true)
	public BudgetResponse get(long userId, String month) {
		return find(userId, month).orElseThrow(BudgetNotFoundException::new);
	}

	// Un solo cálculo del presupuesto con su gastado para GET /presupuestos/{mes} y el resumen (plan 004 D4, R5).
	@Transactional(readOnly = true)
	public Optional<BudgetResponse> find(long userId, String month) {
		MonthRange range = MonthRange.parse(month);
		return budgetRepository.findByUserIdAndMonth(userId, month)
				.map(budget -> toResponse(userId, month, range, budget.getAmount(), budget.getId()));
	}

	// Borrado físico: las líneas caen por ON DELETE CASCADE (plan 003 D11, R23).
	@Transactional
	public void delete(long userId, String month) {
		MonthRange.parse(month);
		Budget budget = budgetRepository.findByUserIdAndMonth(userId, month)
				.orElseThrow(BudgetNotFoundException::new);
		budgetRepository.delete(budget);
	}

	// Inexistente, eliminada, ajena o de tipo INGRESO: mismo error en todos los casos (R20, plan D4).
	private void requireExpenseCategory(long userId, long categoryId) {
		categoryRepository.findVisible(categoryId, userId)
				.filter(category -> category.getType() == MovementType.GASTO)
				.orElseThrow(InvalidCategoryException::new);
	}

	// Gastado del mes: el general cuenta todos los gastos, con o sin línea (plan D9).
	private BudgetResponse toResponse(long userId, String month, MonthRange range, long amount, long budgetId) {
		Long totalSpent = movementRepository.sumAmount(userId, MovementType.GASTO, range.from(), range.to());
		long spent = totalSpent == null ? 0L : totalSpent;
		Map<Long, Long> spentByCategory = new HashMap<>();
		for (CategorySpent row : movementRepository.sumAmountByCategory(userId, MovementType.GASTO, range.from(),
				range.to())) {
			spentByCategory.put(row.categoryId(), row.total());
		}
		List<BudgetResponse.Line> lines = budgetLineRepository.findByBudgetIdOrderedByCategoryName(budgetId).stream()
				.map(line -> {
					long lineSpent = spentByCategory.getOrDefault(line.getCategoryId(), 0L);
					return new BudgetResponse.Line(line.getCategoryId(), line.getAmount(), lineSpent,
							line.getAmount() - lineSpent, lineSpent > line.getAmount());
				}).toList();
		return new BudgetResponse(month, amount, spent, amount - spent, spent > amount, lines);
	}
}
