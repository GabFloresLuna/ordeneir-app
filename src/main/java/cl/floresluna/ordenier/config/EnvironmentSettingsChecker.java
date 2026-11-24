package cl.floresluna.ordenier.config;

import cl.floresluna.ordenier.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Comprueba las cuatro variables de entorno del backend (spec 008). No usa Spring y nunca
 * incluye el valor de una variable en sus mensajes.
 */
public final class EnvironmentSettingsChecker {

	static final String URL_FORMAT = "jdbc:postgresql://<host>:<puerto>/<base>";

	private static final Pattern URL_PATTERN =
			Pattern.compile("^jdbc:postgresql://[^/\\s:?]+:\\d{1,5}/[^\\s/?]+(\\?\\S*)?$");

	private EnvironmentSettingsChecker() {
	}

	public static List<String> check(String url, String user, String password, String secret) {
		List<String> problems = new ArrayList<>();
		if (isBlank(url)) {
			problems.add("Falta DB_URL. Formato esperado: " + URL_FORMAT);
		} else if (!URL_PATTERN.matcher(url).matches()) {
			problems.add("DB_URL no tiene el formato esperado: " + URL_FORMAT);
		}
		if (isBlank(user)) {
			problems.add("Falta DB_USER.");
		}
		if (isBlank(password)) {
			problems.add("Falta DB_PASSWORD.");
		}
		if (isBlank(secret)) {
			problems.add("Falta JWT_SECRET (mínimo " + JwtProperties.MIN_SECRET_BYTES + " bytes).");
		} else if (secret.getBytes(StandardCharsets.UTF_8).length < JwtProperties.MIN_SECRET_BYTES) {
			problems.add("JWT_SECRET es demasiado corto: debe tener al menos "
					+ JwtProperties.MIN_SECRET_BYTES + " bytes.");
		}
		return problems;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
