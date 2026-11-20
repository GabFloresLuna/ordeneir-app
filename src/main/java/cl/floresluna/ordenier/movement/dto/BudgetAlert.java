package cl.floresluna.ordenier.movement.dto;

/**
 * Aviso informativo de exceso de presupuesto al crear o editar un gasto (R25, plan 003 D12).
 * `categoryExceeded` es nulo si la categoría no tiene línea de presupuesto.
 */
public record BudgetAlert(boolean generalExceeded, Boolean categoryExceeded) {
}
