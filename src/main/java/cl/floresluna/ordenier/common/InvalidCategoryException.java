package cl.floresluna.ordenier.common;

/** Categoría inexistente, eliminada, ajena o de otro tipo: se trata igual para no revelar datos (R15). */
public class InvalidCategoryException extends RuntimeException {

	public InvalidCategoryException() {
		super("Categoría inválida");
	}
}
