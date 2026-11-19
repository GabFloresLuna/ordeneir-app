package cl.floresluna.ordenier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

class LoginTest extends AbstractIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";
	private static final String LOGIN = "/api/v1/auth/login";
	private static final String PASSWORD = "clave-segura-123";

	@Autowired
	private JwtDecoder jwtDecoder;

	private long userId;

	@BeforeEach
	void registerUser() throws Exception {
		String body = send(REGISTER, "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		userId = ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	@Test
	void validLoginReturns200WithTokenOf3600SecondsForTheUser() throws Exception {
		String body = login("ana@example.com", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresInSeconds").value(3600))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(PASSWORD);
		Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
		assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
	}

	@Test
	void loginIgnoresEmailCaseAndSurroundingSpaces() throws Exception {
		login("  ANA@Example.COM ", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresInSeconds").value(3600));
	}

	@Test
	void unknownEmailReturns401CredencialesInvalidas() throws Exception {
		login("nadie@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void wrongPasswordReturns401CredencialesInvalidas() throws Exception {
		login("ana@example.com", "otra-clave-123")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void unknownEmailAndWrongPasswordGiveIdenticalResponseBody() throws Exception {
		String unknownEmail = login("nadie@example.com", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		String wrongPassword = login("ana@example.com", "otra-clave-123")
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(unknownEmail).isNotEmpty().isEqualTo(wrongPassword);
	}

	@Test
	void veryLongPasswordIsNotRejectedAsInvalidDataAndReturns401() throws Exception {
		login("ana@example.com", "x".repeat(200))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void missingPasswordReturns400DatosInvalidos() throws Exception {
		assertInvalidData(send(LOGIN, "{\"email\":\"ana@example.com\"}"));
	}

	@Test
	void missingEmailReturns400DatosInvalidos() throws Exception {
		assertInvalidData(send(LOGIN, "{\"password\":\"" + PASSWORD + "\"}"));
	}

	@Test
	void missingBothFieldsReturns400DatosInvalidos() throws Exception {
		assertInvalidData(send(LOGIN, "{}"));
	}

	private void assertInvalidData(ResultActions result) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	private ResultActions login(String email, String password) throws Exception {
		return send(LOGIN, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResultActions send(String url, String json) throws Exception {
		return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
	}
}
