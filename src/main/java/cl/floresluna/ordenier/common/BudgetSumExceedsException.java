package cl.floresluna.ordenier.common;

/** La suma de las líneas por categoría supera el monto general (R19). */
public class BudgetSumExceedsException extends RuntimeException {

	public BudgetSumExceedsException() {
		super("La suma de las categorías supera el presupuesto general");
	}
}
