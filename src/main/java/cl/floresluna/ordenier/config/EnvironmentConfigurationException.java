package cl.floresluna.ordenier.config;

import java.util.List;

/**
 * Las variables de entorno del backend faltan o son inválidas (spec 008). Los problemas nombran
 * variables, nunca valores.
 */
public class EnvironmentConfigurationException extends RuntimeException {

	private final transient List<String> problems;

	public EnvironmentConfigurationException(List<String> problems) {
		super("Configuración de entorno inválida: " + String.join(" ", problems));
		this.problems = List.copyOf(problems);
	}

	public List<String> getProblems() {
		return problems;
	}
}
