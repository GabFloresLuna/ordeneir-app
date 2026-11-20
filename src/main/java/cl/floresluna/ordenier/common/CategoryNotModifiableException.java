package cl.floresluna.ordenier.common;

/** Las categorías predefinidas son de solo lectura (R9). */
public class CategoryNotModifiableException extends RuntimeException {

	public CategoryNotModifiableException() {
		super("Categoría no modificable");
	}
}
