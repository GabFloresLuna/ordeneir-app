package cl.floresluna.ordenier.common;

/** Categoría inexistente, eliminada o ajena: se trata igual para no revelar datos (R10). */
public class CategoryNotFoundException extends RuntimeException {

	public CategoryNotFoundException() {
		super("Categoría no encontrada");
	}
}
