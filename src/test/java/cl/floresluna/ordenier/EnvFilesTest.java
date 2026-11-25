package cl.floresluna.ordenier;

import static org.assertj.core.api.Assertions.assertThat;

import cl.floresluna.ordenier.config.EnvironmentSettingsChecker;
import cl.floresluna.ordenier.security.JwtProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Pruebas de archivos de la spec 008 (se ejecutan desde ordenier/). */
class EnvFilesTest {

	private static final Set<String> NAMES = Set.of("DB_URL", "DB_USER", "DB_PASSWORD", "JWT_SECRET");

	private static List<String> lines(String file) throws IOException {
		return Files.readAllLines(Path.of(file), StandardCharsets.UTF_8);
	}

	private static Map<String, String> envExample() throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		for (String line : lines(".env.example")) {
			if (line.isBlank() || line.startsWith("#")) {
				continue;
			}
			int eq = line.indexOf('=');
			assertThat(eq).as("línea con forma NOMBRE=valor").isPositive();
			values.put(line.substring(0, eq).trim(), line.substring(eq + 1));
		}
		return values;
	}

	private static boolean ignores(String pattern, String fileName) {
		String glob = pattern.startsWith("/") ? pattern.substring(1) : pattern;
		String regex = Pattern.quote(glob).replace("*", "\\E[^/]*\\Q");
		return fileName.matches(regex);
	}

	private static boolean gitignoreIgnores(String fileName) throws IOException {
		boolean ignored = false;
		for (String raw : lines(".gitignore")) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			boolean negated = line.startsWith("!");
			String pattern = negated ? line.substring(1) : line;
			if (ignores(pattern, fileName)) {
				ignored = !negated;
			}
		}
		return ignored;
	}

	@Test
	void envExampleDefinesExactlyTheFourVariables() throws IOException {
		assertThat(envExample().keySet()).containsExactlyInAnyOrderElementsOf(NAMES);
	}

	@Test
	void envExampleUrlMeetsTheUrlFormat() throws IOException {
		String url = envExample().get("DB_URL");
		assertThat(EnvironmentSettingsChecker.check(url, "usuario", "clave", "a".repeat(JwtProperties.MIN_SECRET_BYTES)))
				.isEmpty();
	}

	@Test
	void envExampleSecretIsShorterThanTheMinimumSoItCannotBootAsIs() throws IOException {
		String secret = envExample().get("JWT_SECRET");
		assertThat(secret.getBytes(StandardCharsets.UTF_8).length).isLessThan(JwtProperties.MIN_SECRET_BYTES);
	}

	@Test
	void gitignoreIgnoresEnvButNotEnvExample() throws IOException {
		assertThat(gitignoreIgnores(".env")).isTrue();
		assertThat(gitignoreIgnores(".env.example")).isFalse();
	}

	@Test
	void applicationYmlUsesOnlyTheFourVariableNames() throws IOException {
		String yml = Files.readString(Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?\\}").matcher(yml);
		Set<String> found = new LinkedHashSet<>();
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		assertThat(found).containsExactlyInAnyOrderElementsOf(NAMES);
	}

	@Test
	void readmeHasTheEnvironmentVariablesSectionWithNamesAndLoadCommand() throws IOException {
		String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
		assertThat(readme).contains("## Variables de entorno");
		assertThat(readme).contains("DB_URL", "DB_USER", "DB_PASSWORD", "JWT_SECRET");
		assertThat(readme).contains(". .\\scripts\\load-env.ps1");
		assertThat(readme).contains(".\\mvnw spring-boot:run");
		assertThat(readme).contains(".\\mvnw.cmd test");
	}

	@Test
	void readmeExplainsTheJdbcErrorAndTheExecutionPolicy() throws IOException {
		String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
		assertThat(readme).contains("'url' must start with \"jdbc\"");
		assertThat(readme).contains("Set-ExecutionPolicy");
	}
}
