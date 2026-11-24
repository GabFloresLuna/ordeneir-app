package cl.floresluna.ordenier.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EnvironmentSettingsCheckerTest {

	private static final String URL = "jdbc:postgresql://localhost:5432/ordenier";
	private static final String SECRET = "a".repeat(32);

	private static List<String> check(String url, String user, String password, String secret) {
		return EnvironmentSettingsChecker.check(url, user, password, secret);
	}

	@Test
	void validSettingsHaveNoProblems() {
		assertThat(check(URL, "postgres", "clave", SECRET)).isEmpty();
	}

	@Test
	void urlWithParametersIsAccepted() {
		assertThat(check(URL + "?sslmode=disable&currentSchema=public", "postgres", "clave", SECRET)).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
			"   ",
			"postgresql://localhost:5432/ordenier",
			"jdbc:mysql://localhost:5432/ordenier",
			"jdbc:postgresql://localhost/ordenier",
			"jdbc:postgresql://localhost:5432",
			"jdbc:postgresql://localhost:5432/",
			"jdbc:postgresql://:5432/ordenier",
			"jdbc:postgresql://localhost:abc/ordenier",
			"jdbc:postgresql://localhost:5432/orde nier",
			"localhost:5432/ordenier" })
	void invalidUrlIsReportedNamingTheVariableAndFormat(String url) {
		List<String> problems = check(url, "postgres", "clave", SECRET);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0)).contains("DB_URL").contains("jdbc:postgresql://<host>:<puerto>/<base>");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void emptyUserIsReported(String user) {
		List<String> problems = check(URL, user, "clave", SECRET);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0)).contains("DB_USER");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void emptyPasswordIsReported(String password) {
		List<String> problems = check(URL, "postgres", password, SECRET);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0)).contains("DB_PASSWORD");
	}

	@Test
	void secretOf31BytesIsReportedWithTheMinimum() {
		List<String> problems = check(URL, "postgres", "clave", "a".repeat(31));

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0)).contains("JWT_SECRET").contains("32");
	}

	@Test
	void secretOf32BytesIsAccepted() {
		assertThat(check(URL, "postgres", "clave", "a".repeat(32))).isEmpty();
	}

	@Test
	void secretLengthIsMeasuredInUtf8Bytes() {
		// 'ñ' ocupa 2 bytes: 15 'ñ' + 2 'a' = 17 caracteres, 32 bytes
		assertThat(check(URL, "postgres", "clave", "ñ".repeat(15) + "aa")).isEmpty();
		// 15 'ñ' + 1 'a' = 31 bytes
		assertThat(check(URL, "postgres", "clave", "ñ".repeat(15) + "a")).hasSize(1);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void missingSecretIsReported(String secret) {
		List<String> problems = check(URL, "postgres", "clave", secret);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0)).contains("JWT_SECRET");
	}

	@Test
	void severalProblemsAreListedTogether() {
		List<String> problems = check(null, "", null, "corto");

		assertThat(problems).hasSize(4);
		assertThat(problems.get(0)).contains("DB_URL");
		assertThat(problems.get(1)).contains("DB_USER");
		assertThat(problems.get(2)).contains("DB_PASSWORD");
		assertThat(problems.get(3)).contains("JWT_SECRET");
	}

	@Test
	void messagesNeverIncludeTheTestedValues() {
		String badUrl = "valor-url-secreto";
		String badSecret = "valor-secreto-corto";
		String password = "   ";

		List<String> problems = check(badUrl, "usuario-visible", password, badSecret);

		assertThat(String.join("\n", problems))
				.doesNotContain(badUrl)
				.doesNotContain(badSecret)
				.doesNotContain("usuario-visible");
	}
}
