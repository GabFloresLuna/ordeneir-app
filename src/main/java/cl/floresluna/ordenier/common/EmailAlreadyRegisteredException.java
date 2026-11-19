package cl.floresluna.ordenier.common;

public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException() {
		super("El email ya está registrado");
	}
}
