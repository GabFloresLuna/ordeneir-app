package cl.floresluna.ordenier.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.diagnostics.FailureAnalysis;

class EnvironmentStartupTest {

	private static final String SECRET = "s".repeat(32);
	private static final String URL = "jdbc:postgresql://localhost:5432/ordenier";

	static class Marker {
	}

	private final AtomicBoolean otherBeanCreated = new AtomicBoolean(false);

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(EnvironmentValidationConfig.class)
			.withBean(Marker.class, () -> {
				otherBeanCreated.set(true);
				return new Marker();
			});

	@Test
	void contextStartsWithValidVariables() {
		runner.withPropertyValues(validValues()).run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void contextFailsWhenAllVariablesAreMissing() {
		runner.run(context -> {
			assertThat(context).hasFailed();
			EnvironmentConfigurationException failure = rootFailure(context.getStartupFailure());
			assertThat(failure.getProblems()).hasSize(4);
			assertThat(failure.getMessage()).contains("DB_URL", "DB_USER", "DB_PASSWORD", "JWT_SECRET");
		});
	}

	@Test
	void contextFailsWhenVariablesAreEmpty() {
		runner.withPropertyValues("spring.datasource.url=", "spring.datasource.username=",
				"spring.datasource.password=", "app.jwt.secret=").run(context -> {
					assertThat(context).hasFailed();
					assertThat(rootFailure(context.getStartupFailure()).getProblems()).hasSize(4);
				});
	}

	@Test
	void contextFailsWithMalformedUrlNamingDbUrlWithoutItsValue() {
		String badUrl = "localhost:5432/secreto-base";
		runner.withPropertyValues(
				"spring.datasource.url=" + badUrl, "spring.datasource.username=user",
				"spring.datasource.password=pass-xyz", "app.jwt.secret=" + SECRET).run(context -> {
					assertThat(context).hasFailed();
					EnvironmentConfigurationException failure = rootFailure(context.getStartupFailure());
					assertThat(failure.getMessage()).contains("DB_URL", "jdbc:postgresql://<host>:<puerto>/<base>");
					assertNoValues(failure.getMessage(), badUrl, "pass-xyz", SECRET);
				});
	}

	@Test
	void contextFailsWithShortSecretNamingJwtSecretWithoutItsValue() {
		String shortSecret = "corto-secreto-31-bytes-aaaaaaaa";
		assertThat(shortSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(31);
		runner.withPropertyValues(
				"spring.datasource.url=" + URL, "spring.datasource.username=user",
				"spring.datasource.password=pass-xyz", "app.jwt.secret=" + shortSecret).run(context -> {
					assertThat(context).hasFailed();
					EnvironmentConfigurationException failure = rootFailure(context.getStartupFailure());
					assertThat(failure.getMessage()).contains("JWT_SECRET", "32");
					assertNoValues(failure.getMessage(), shortSecret, "pass-xyz", URL);
				});
	}

	@Test
	void validationFailsBeforeAnyOtherBeanIsCreated() {
		runner.run(context -> {
			assertThat(context).hasFailed();
			assertThat(otherBeanCreated).isFalse();
		});
	}

	@Test
	void failureAnalyzerShowsVariableNamesAndLoadActionWithoutValues() {
		String badUrl = "valor-secreto-url";
		EnvironmentConfigurationException exception = new EnvironmentConfigurationException(
				EnvironmentSettingsChecker.check(badUrl, "", "p", SECRET));
		FailureAnalysis analysis = new EnvironmentFailureAnalyzer().analyze(exception);
		assertThat(analysis).isNotNull();
		assertThat(analysis.getDescription()).contains("DB_URL", "DB_USER");
		assertThat(analysis.getAction()).contains("load-env.ps1");
		assertNoValues(analysis.getDescription() + analysis.getAction(), badUrl, SECRET);
	}

	@Test
	void failureAnalyzerIsRegisteredInSpringFactories() throws java.io.IOException {
		var urls = java.util.Collections.list(
				getClass().getClassLoader().getResources("META-INF/spring.factories"));
		boolean registered = false;
		for (var url : urls) {
			var properties = new java.util.Properties();
			try (var in = url.openStream()) {
				properties.load(in);
			}
			String analyzers = properties.getProperty("org.springframework.boot.diagnostics.FailureAnalyzer", "");
			registered |= analyzers.contains(EnvironmentFailureAnalyzer.class.getName());
		}
		assertThat(registered).isTrue();
	}

	private static String[] validValues() {
		return new String[] { "spring.datasource.url=" + URL, "spring.datasource.username=user",
				"spring.datasource.password=pass", "app.jwt.secret=" + SECRET };
	}

	private static EnvironmentConfigurationException rootFailure(Throwable failure) {
		Throwable current = failure;
		while (current != null && !(current instanceof EnvironmentConfigurationException)) {
			current = current.getCause();
		}
		assertThat(current).isInstanceOf(EnvironmentConfigurationException.class);
		return (EnvironmentConfigurationException) current;
	}

	private static void assertNoValues(String text, String... values) {
		for (String value : values) {
			assertThat(text).doesNotContain(value);
		}
	}
}
