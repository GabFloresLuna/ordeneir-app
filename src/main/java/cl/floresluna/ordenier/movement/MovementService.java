package cl.floresluna.ordenier.movement;

import cl.floresluna.ordenier.category.Category;
import cl.floresluna.ordenier.category.CategoryRepository;
import cl.floresluna.ordenier.common.InvalidCategoryException;
import cl.floresluna.ordenier.common.InvalidDataException;
import cl.floresluna.ordenier.common.MovementNotFoundException;
import cl.floresluna.ordenier.common.PageResponse;
import cl.floresluna.ordenier.movement.dto.MovementRequest;
import cl.floresluna.ordenier.movement.dto.MovementResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MovementService {

	private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
	private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");
	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt")
			.and(Sort.by(Sort.Direction.DESC, "id"));

	private final MovementRepository movementRepository;
	private final CategoryRepository categoryRepository;
	private final Clock clock;

	public MovementService(MovementRepository movementRepository, CategoryRepository categoryRepository,
			Clock clock) {
		this.movementRepository = movementRepository;
		this.categoryRepository = categoryRepository;
		this.clock = clock;
	}

	// El dueño viene siempre del JWT, nunca del cuerpo (R19, plan D5).
	public MovementResponse create(long userId, MovementRequest request) {
		MovementRequest.Validated data = request.validate(clock);
		requireValidCategory(userId, data);
		Movement saved = movementRepository.save(new Movement(userId, data.amount(), data.type(),
				data.categoryId(), data.occurredAt(), data.description()));
		return MovementResponse.from(saved);
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
		return MovementResponse.from(movement);
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
			if (!MONTH_PATTERN.matcher(mes).matches()) {
				throw new InvalidDataException();
			}
			YearMonth month = YearMonth.parse(mes);
			Instant from = month.atDay(1).atStartOfDay(SANTIAGO).toInstant();
			Instant to = month.plusMonths(1).atDay(1).atStartOfDay(SANTIAGO).toInstant();
			result = movementRepository
					.findByUserIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
							userId, from, to, pageable);
		}
		return PageResponse.from(result, MovementResponse::from);
	}
}
