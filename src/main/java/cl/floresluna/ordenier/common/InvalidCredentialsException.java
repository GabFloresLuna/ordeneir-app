package cl.floresluna.ordenier.common;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Credenciales inválidas");
	}
}
