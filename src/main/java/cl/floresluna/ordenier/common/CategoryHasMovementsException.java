package cl.floresluna.ordenier.common;

/** La categoría tiene al menos un movimiento no eliminado: no se puede eliminar (R12). */
public class CategoryHasMovementsException extends RuntimeException {

	public CategoryHasMovementsException() {
		super("Categoría con movimientos");
	}
}
