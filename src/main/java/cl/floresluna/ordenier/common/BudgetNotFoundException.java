package cl.floresluna.ordenier.common;

/** El usuario no tiene presupuesto para ese mes (R22, R23). */
public class BudgetNotFoundException extends RuntimeException {

	public BudgetNotFoundException() {
		super("Presupuesto no encontrado");
	}
}
