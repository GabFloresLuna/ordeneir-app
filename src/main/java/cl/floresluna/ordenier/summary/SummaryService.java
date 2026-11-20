package cl.floresluna.ordenier.summary;

import cl.floresluna.ordenier.common.MonthRange;
import cl.floresluna.ordenier.movement.CategorySpent;
import cl.floresluna.ordenier.movement.MovementRepository;
import cl.floresluna.ordenier.movement.MovementType;
import cl.floresluna.ordenier.summary.dto.SummaryResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

	private final MovementRepository movementRepository;

	public SummaryService(MovementRepository movementRepository) {
		this.movementRepository = movementRepository;
	}

	// El mes se valida antes de consultar (plan 004 D2). El dueño viene del JWT (R10). Un mes sin movimientos
	// o futuro no es un caso especial: las consultas devuelven vacío y salen ceros (R6, D8).
	// T1: las listas por categoría y el presupuesto salen vacíos/nulo; T2 y T3 los completan.
	@Transactional(readOnly = true)
	public SummaryResponse get(long userId, String month) {
		MonthRange range = MonthRange.parse(month);
		long totalIncome = total(userId, MovementType.INGRESO, range);
		long totalExpenses = total(userId, MovementType.GASTO, range);
		return new SummaryResponse(month, totalIncome, totalExpenses, totalIncome - totalExpenses, List.of(),
				List.of(), null);
	}

	// Cada total es la suma de las filas por categoría de su tipo (plan 004 D3).
	private long total(long userId, MovementType type, MonthRange range) {
		return movementRepository.sumAmountByCategory(userId, type, range.from(), range.to()).stream()
				.mapToLong(CategorySpent::total).sum();
	}
}
