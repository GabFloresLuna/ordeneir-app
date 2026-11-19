package cl.floresluna.ordenier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class RegisterValidationTest extends AbstractIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";
	private static final String EMAIL = "ana@example.com";
	private static final String PASSWORD = "clave-segura-123";

	// ---- email: largo máximo 254 (medido tras trim y minúsculas)

	@Test
	void emailOf254CharactersIsAccepted() throws Exception {
		String email = emailOfLength(254);
		assertThat(email).hasSize(254);

		registerJson(email, PASSWORD).andExpect(status().isCreated());
		assertUserCount(1);
	}

	@Test
	void emailOf254CharactersPlusSurroundingSpacesIsAccepted() throws Exception {
		registerJson("  " + emailOfLength(254).toUpperCase() + " ", PASSWORD).andExpect(status().isCreated());
		assertUserCount(1);
	}

	@Test
	void emailOf255CharactersReturns400() throws Exception {
		String email = emailOfLength(255);
		assertThat(email).hasSize(255);

		assertInvalid(registerJson(email, PASSWORD));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "ana", "ana.example.com", "ana@", "@example.com", "ana@@example.com",
			"ana example@example.com" })
	void emailWithInvalidFormatReturns400(String email) throws Exception {
		assertInvalid(registerJson(email, PASSWORD));
	}

	// ---- contraseña: 8 a 64 caracteres y a lo más 72 bytes en UTF-8

	@Test
	void passwordOf7CharactersReturns400() throws Exception {
		assertInvalid(registerJson(EMAIL, "a".repeat(7)));
	}

	@Test
	void passwordOf8CharactersIsAccepted() throws Exception {
		registerJson(EMAIL, "a".repeat(8)).andExpect(status().isCreated());
		assertUserCount(1);
	}

	@Test
	void passwordOf64CharactersIsAccepted() throws Exception {
		registerJson(EMAIL, "a".repeat(64)).andExpect(status().isCreated());
		assertUserCount(1);
	}

	@Test
	void passwordOf65CharactersReturns400() throws Exception {
		assertInvalid(registerJson(EMAIL, "a".repeat(65)));
	}

	@Test
	void passwordOf64EnyeCharactersReturns400BecauseOfTheByteLimit() throws Exception {
		assertInvalid(registerJson(EMAIL, "ñ".repeat(64)));
	}

	@Test
	void passwordOf72BytesInUtf8IsAccepted() throws Exception {
		registerJson(EMAIL, "ñ".repeat(36)).andExpect(status().isCreated());
		assertUserCount(1);
	}

	@Test
	void passwordOf74BytesInUtf8Returns400() throws Exception {
		assertInvalid(registerJson(EMAIL, "ñ".repeat(37)));
	}

	// ---- campos faltantes y JSON roto

	@Test
	void missingEmailReturns400() throws Exception {
		assertInvalid(registerRaw("{\"password\":\"" + PASSWORD + "\"}"));
	}

	@Test
	void missingPasswordReturns400() throws Exception {
		assertInvalid(registerRaw("{\"email\":\"" + EMAIL + "\"}"));
	}

	@Test
	void emptyObjectReturns400() throws Exception {
		assertInvalid(registerRaw("{}"));
	}

	@Test
	void nullFieldsReturn400() throws Exception {
		assertInvalid(registerRaw("{\"email\":null,\"password\":null}"));
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		assertInvalid(registerRaw("{\"email\":\"" + EMAIL + "\",\"password\":"));
	}

	@Test
	void missingBodyReturns400() throws Exception {
		assertInvalid(registerRaw(""));
	}

	// ---- ayudas

	private void assertInvalid(ResultActions result) throws Exception {
		String body = result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain(PASSWORD);
		assertUserCount(0);
	}

	private void assertUserCount(int expected) {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		assertThat(count).isEqualTo(expected);
	}

	private ResultActions registerJson(String email, String password) throws Exception {
		return registerRaw("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResultActions registerRaw(String body) throws Exception {
		return mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	/** Email de formato válido (local de 64, etiquetas de dominio de a lo más 63) con el largo exacto pedido. */
	private static String emailOfLength(int length) {
		String local = "a".repeat(64);
		int domainLength = length - local.length() - 1;
		int remaining = domainLength - 63 - 1 - 63 - 1;
		return local + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(remaining);
	}
}
