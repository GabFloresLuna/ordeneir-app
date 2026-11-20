package cl.floresluna.ordenier.common;

public class InvalidDataException extends RuntimeException {

	public InvalidDataException() {
		super("Datos inválidos");
	}
}
