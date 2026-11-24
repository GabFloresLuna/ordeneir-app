package cl.floresluna.ordenier.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Muestra el error de variables de entorno como "Description / Action", sin pila de errores. */
public class EnvironmentFailureAnalyzer extends AbstractFailureAnalyzer<EnvironmentConfigurationException> {

	static final String ACTION =
			"Carga el archivo .env en esta sesión de PowerShell con: . .\\scripts\\load-env.ps1 "
					+ "(si no existe, copia .env.example a .env y complétalo). Más detalle en README.md.";

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, EnvironmentConfigurationException cause) {
		String description = "Las variables de entorno del backend no son válidas:\n- "
				+ String.join("\n- ", cause.getProblems());
		return new FailureAnalysis(description, ACTION, cause);
	}
}
