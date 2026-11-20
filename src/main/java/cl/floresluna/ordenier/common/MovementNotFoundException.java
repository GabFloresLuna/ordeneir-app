package cl.floresluna.ordenier.common;

/** Movimiento inexistente, eliminado o ajeno: se trata igual para no revelar datos (R12). */
public class MovementNotFoundException extends RuntimeException {

	public MovementNotFoundException() {
		super("Movimiento no encontrado");
	}
}
