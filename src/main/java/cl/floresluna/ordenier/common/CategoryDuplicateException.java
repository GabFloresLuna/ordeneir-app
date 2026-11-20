package cl.floresluna.ordenier.common;

/** Ya existe una categoría visible del mismo tipo con ese nombre (R6). */
public class CategoryDuplicateException extends RuntimeException {

	public CategoryDuplicateException() {
		super("Categoría duplicada");
	}
}
