package cl.floresluna.ordenier.category;

import cl.floresluna.ordenier.budget.BudgetLineRepository;
import cl.floresluna.ordenier.category.dto.CategoryRequest;
import cl.floresluna.ordenier.category.dto.CategoryResponse;
import cl.floresluna.ordenier.common.CategoryDuplicateException;
import cl.floresluna.ordenier.common.CategoryHasMovementsException;
import cl.floresluna.ordenier.common.CategoryNotFoundException;
import cl.floresluna.ordenier.common.CategoryNotModifiableException;
import cl.floresluna.ordenier.movement.MovementRepository;
import cl.floresluna.ordenier.movement.MovementType;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final MovementRepository movementRepository;
	private final BudgetLineRepository budgetLineRepository;

	public CategoryService(CategoryRepository categoryRepository, MovementRepository movementRepository,
			BudgetLineRepository budgetLineRepository) {
		this.categoryRepository = categoryRepository;
		this.movementRepository = movementRepository;
		this.budgetLineRepository = budgetLineRepository;
	}

	// Predefinidas y propias no eliminadas, ordenadas por tipo y nombre; `tipo` filtra (R7, plan D4).
	public List<CategoryResponse> list(long userId, String tipo) {
		List<Category> categories = tipo == null
				? categoryRepository.findAllVisible(userId)
				: categoryRepository.findAllVisibleByType(userId, CategoryRequest.parseType(tipo));
		return categories.stream().map(CategoryResponse::from).toList();
	}

	// El dueño viene siempre del JWT, nunca del cuerpo (R28). El duplicado se comprueba antes y,
	// si aun así salta el índice único de la BD, también responde 409 (R6, plan D5).
	public CategoryResponse create(long userId, CategoryRequest request) {
		CategoryRequest.Validated data = request.validate();
		if (categoryRepository.existsVisibleByTypeAndName(userId, data.type(), data.name())) {
			throw new CategoryDuplicateException();
		}
		try {
			return CategoryResponse.from(
					categoryRepository.saveAndFlush(new Category(userId, data.name(), data.type())));
		} catch (DataIntegrityViolationException e) {
			throw new CategoryDuplicateException();
		}
	}

	// Cuerpo válido primero (R5); luego 404 si no es visible (R10) y 403 si es predefinida (R9, plan D13).
	// Solo cambia el nombre; el duplicado excluye a la propia categoría (R8, plan D5).
	@Transactional
	public CategoryResponse update(long userId, long id, CategoryRequest request) {
		String name = request.validateName();
		Category category = categoryRepository.findVisible(id, userId).orElseThrow(CategoryNotFoundException::new);
		if (category.getUserId() == null) {
			throw new CategoryNotModifiableException();
		}
		if (categoryRepository.existsOtherVisibleByTypeAndName(userId, category.getType(), name, id)) {
			throw new CategoryDuplicateException();
		}
		category.rename(name);
		try {
			return CategoryResponse.from(categoryRepository.saveAndFlush(category));
		} catch (DataIntegrityViolationException e) {
			throw new CategoryDuplicateException();
		}
	}

	// Orden de plan D13: 404 si no es visible (R10), 403 si es predefinida (R9), 409 si tiene movimientos
	// no eliminados (R12); si no, borrado lógico y se descartan sus líneas de presupuesto (R11).
	@Transactional
	public void delete(long userId, long id) {
		Category category = categoryRepository.findVisible(id, userId).orElseThrow(CategoryNotFoundException::new);
		if (category.getUserId() == null) {
			throw new CategoryNotModifiableException();
		}
		if (movementRepository.existsByCategoryIdAndDeletedAtIsNull(id)) {
			throw new CategoryHasMovementsException();
		}
		category.markDeleted(Instant.now());
		categoryRepository.saveAndFlush(category);
		budgetLineRepository.deleteByCategoryId(id);
	}
}
