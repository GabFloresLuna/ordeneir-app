package cl.floresluna.ordenier.category;

import cl.floresluna.ordenier.category.dto.CategoryRequest;
import cl.floresluna.ordenier.category.dto.CategoryResponse;
import cl.floresluna.ordenier.security.AuthenticatedUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoryController {

	private final CategoryService categoryService;
	private final AuthenticatedUser authenticatedUser;

	public CategoryController(CategoryService categoryService, AuthenticatedUser authenticatedUser) {
		this.categoryService = categoryService;
		this.authenticatedUser = authenticatedUser;
	}

	@GetMapping
	public List<CategoryResponse> list(@RequestParam(required = false) String tipo) {
		return categoryService.list(authenticatedUser.id(), tipo);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoryResponse create(@RequestBody CategoryRequest request) {
		return categoryService.create(authenticatedUser.id(), request);
	}

	@PutMapping("/{id}")
	public CategoryResponse update(@PathVariable long id, @RequestBody CategoryRequest request) {
		return categoryService.update(authenticatedUser.id(), id, request);
	}
}
