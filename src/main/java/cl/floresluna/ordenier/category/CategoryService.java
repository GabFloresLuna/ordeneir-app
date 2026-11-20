package cl.floresluna.ordenier.category;

import cl.floresluna.ordenier.category.dto.CategoryRequest;
import cl.floresluna.ordenier.category.dto.CategoryResponse;
import cl.floresluna.ordenier.common.CategoryDuplicateException;
import cl.floresluna.ordenier.movement.MovementType;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;

	public CategoryService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
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
}
