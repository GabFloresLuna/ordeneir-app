package cl.floresluna.ordenier.movement;

import cl.floresluna.ordenier.budget.BudgetLineRepository;
import cl.floresluna.ordenier.budget.BudgetRepository;
import cl.floresluna.ordenier.category.Category;
import cl.floresluna.ordenier.category.CategoryRepository;
import cl.floresluna.ordenier.common.InvalidCategoryException;
import cl.floresluna.ordenier.common.MonthRange;
import cl.floresluna.ordenier.common.MovementNotFoundException;
import cl.floresluna.ordenier.common.PageResponse;
import cl.floresluna.ordenier.movement.dto.BudgetAlert;
import cl.floresluna.ordenier.movement.dto.MovementRequest;
import cl.floresluna.ordenier.movement.dto.MovementResponse;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovementService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt")
			.and(Sort.by(Sort.Direction.DESC, "id"));

	private final MovementRepository movementRepository;
	private final CategoryRepository categoryRepository;
	private final BudgetRepository budgetRepository;
	private final BudgetLineRepository budgetLineRepository;
	private final Clock clock;

	public MovementService(MovementRepository movementRepository, CategoryRepository categoryRepository,
			BudgetRepository budgetRepository, BudgetLineRepository budgetLineRepository, Clock clock) {
		this.movementRepository = movementRepository;
		this.categoryRepository = categoryRepository;
		this.budgetRepository = budgetRepository;
		this.budgetLineRepository = budgetLineRepository;
		this.clock = clock;
	}

	// El dueño viene siempre del JWT, nunca del cuerpo (R19, plan D5).
	@Transactional
	public MovementResponse create(long userId, MovementRequest request) {
		MovementRequest.Validated data = request.validate(clock);
		requireValidCategory(userId, data);
		Movement saved = movementRepository.save(new Movement(userId, data.amount(), data.type(),
				data.categoryId(), data.occurredAt(), data.description()));
		return MovementResponse.from(saved, budgetAlert(userId, saved));
	}

	// Aviso informativo (R25-R27, plan 003 D12): solo para gastos de un mes con presupuesto; el gasto del mes
	// ya incluye este movimiento. Nunca rechaza nada.
	private BudgetAlert budgetAlert(long userId, Movement movement) {
		if (movement.getType() != MovementType.GASTO) {
			return null;
		}
		String month = MonthRange.monthOf(movement.getOccurredAt());
		MonthRange range = MonthRange.parse(month);
		return budgetRepository.findByUserIdAndMonth(userId, month).map(budget -> {
			Long total = movementRepository.sumAmount(userId, MovementType.GASTO, range.from(), range.to());
			boolean generalExceeded = (total == null ? 0L : total) > budget.getAmount();
			Boolean categoryExceeded = budgetLineRepository
					.findByBudgetIdAndCategoryId(budget.getId(), movement.getCategoryId())
					.map(line -> {
						long spent = movementRepository.sumAmountByCategory(userId, MovementType.GASTO,
								range.from(), range.to()).stream()
								.filter(row -> row.categoryId().equals(movement.getCategoryId()))
								.mapToLong(row -> row.total()).sum();
						return spent > line.getAmount();
					}).orElse(null);
			return new BudgetAlert(generalExceeded, categoryExceeded);
		}).orElse(null);
	}

	// Inexistente, eliminada, ajena o de otro tipo: mismo error en todos los casos (R15, plan D7).
	private void requireValidCategory(long userId, MovementRequest.Validated data) {
		Category category = categoryRepository.findVisible(data.categoryId(), userId)
				.orElseThrow(InvalidCategoryException::new);
		if (category.getType() != data.type()) {
			throw new InvalidCategoryException();
		}
	}

	// Inexistente, eliminado o ajeno dan la misma respuesta (R12, plan D6).
	public MovementResponse get(long userId, long id) {
		return movementRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
				.map(MovementResponse::from)
				.orElseThrow(MovementNotFoundException::new);
	}

	// Reemplaza los campos editables; se valida antes de buscar y todo va en una transacción (R14-R16, plan D10).
	@Transactional
	public MovementResponse update(long userId, long id, MovementRequest request) {
		MovementRequest.Validated data = request.validate(clock);
		requireValidCategory(userId, data);
		Movement movement = movementRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
				.orElseThrow(MovementNotFoundException::new);
		movement.replace(data.amount(), data.type(), data.categoryId(), data.occurredAt(), data.description());
		return MovementResponse.from(movement, budgetAlert(userId, movement));
	}

	// Borrado lógico: marca `deleted_at`; inexistente, eliminado o ajeno dan 404 (R17, R18, plan D6).
	@Transactional
	public void delete(long userId, long id) {
		Movement movement = movementRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
				.orElseThrow(MovementNotFoundException::new);
		movement.markDeleted(clock.instant());
	}

	// Solo movimientos del usuario y no eliminados; `mes` (AAAA-MM) va en hora de Santiago (R6-R10, plan D7, D8).
	public PageResponse<MovementResponse> list(long userId, String mes, int page, int size) {
		PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
		Page<Movement> result;
		if (mes == null) {
			result = movementRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
		} else {
			MonthRange range = MonthRange.parse(mes);
			result = movementRepository
					.findByUserIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
							userId, range.from(), range.to(), pageable);
		}
		return PageResponse.from(result, MovementResponse::from);
	}
}
