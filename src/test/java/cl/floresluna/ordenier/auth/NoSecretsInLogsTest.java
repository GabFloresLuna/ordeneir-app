package cl.floresluna.ordenier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.auth.dto.LoginRequest;
import cl.floresluna.ordenier.auth.dto.RegisterRequest;
import cl.floresluna.ordenier.user.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// R13: ninguna respuesta ni registro de log incluye contraseñas ni hashes.
class NoSecretsInLogsTest extends AbstractIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";
	private static final String LOGIN = "/api/v1/auth/login";
	private static final String EMAIL = "ana@example.com";
	private static final String PASSWORD = "Zq7-clave-unica-de-prueba";
	private static final String WRONG_PASSWORD = "Wx9-otra-clave-de-prueba";

	// Se sube el nivel de estos loggers para que, si algo imprime la petición, el test lo vea.
	private static final Map<String, Level> LEVELS_FOR_THE_TEST = Map.of(
			"cl.floresluna.ordenier", Level.TRACE,
			"org.springframework.web", Level.TRACE,
			"org.springframework.security", Level.DEBUG,
			"org.hibernate.SQL", Level.DEBUG);

	private final Map<String, Level> originalLevels = new HashMap<>();
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void startCapturingLogs() {
		LEVELS_FOR_THE_TEST.forEach((name, level) -> {
			Logger logger = (Logger) LoggerFactory.getLogger(name);
			originalLevels.put(name, logger.getLevel());
			logger.setLevel(level);
		});
		appender = new ListAppender<>();
		appender.start();
		rootLogger().addAppender(appender);
	}

	@AfterEach
	void stopCapturingLogs() {
		rootLogger().detachAppender(appender);
		appender.stop();
		originalLevels.forEach((name, level) -> ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
	}

	@Test
	void successfulRegisterAndLoginLogNeitherPasswordNorHash() throws Exception {
		send(REGISTER, EMAIL, PASSWORD).andExpect(status().isCreated());
		send(LOGIN, EMAIL, PASSWORD).andExpect(status().isOk());

		assertNoSecretsInLogs(PASSWORD);
	}

	@Test
	void failedRegisterAndLoginLogNeitherPasswordNorHash() throws Exception {
		send(REGISTER, EMAIL, PASSWORD).andExpect(status().isCreated());

		send(REGISTER, EMAIL, WRONG_PASSWORD).andExpect(status().isConflict());
		send(REGISTER, "no-es-un-email", WRONG_PASSWORD).andExpect(status().isBadRequest());
		send(REGISTER, "otra@example.com", "corta").andExpect(status().isBadRequest());
		send(LOGIN, EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
		send(LOGIN, "nadie@example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
		mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + WRONG_PASSWORD + "\""))
				.andExpect(status().isBadRequest());

		assertNoSecretsInLogs(PASSWORD, WRONG_PASSWORD);
	}

	@Test
	void registerRequestToStringHidesThePassword() {
		assertThat(new RegisterRequest(EMAIL, PASSWORD).toString()).doesNotContain(PASSWORD);
	}

	@Test
	void loginRequestToStringHidesThePassword() {
		assertThat(new LoginRequest(EMAIL, PASSWORD).toString()).doesNotContain(PASSWORD);
	}

	@Test
	void userToStringHidesThePasswordHash() {
		String hash = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

		assertThat(new User(EMAIL, hash).toString()).doesNotContain(hash);
	}

	private void assertNoSecretsInLogs(String... passwords) {
		List<String> lines = appender.list.stream().map(NoSecretsInLogsTest::asText).toList();
		// Si no se capturó nada, el test no estaría comprobando nada.
		assertThat(lines).isNotEmpty();

		List<String> secrets = new java.util.ArrayList<>(List.of(passwords));
		secrets.addAll(jdbcTemplate.queryForList("SELECT password_hash FROM users", String.class));
		for (String secret : secrets) {
			assertThat(lines).as("logs that contain a secret").noneMatch(line -> line.contains(secret));
		}
	}

	private static String asText(ILoggingEvent event) {
		String text = event.getFormattedMessage();
		if (event.getThrowableProxy() != null) {
			text += "\n" + ThrowableProxyUtil.asString(event.getThrowableProxy());
		}
		return text;
	}

	private static Logger rootLogger() {
		return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
	}

	private ResultActions send(String url, String email, String password) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
	}
}
