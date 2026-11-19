package cl.floresluna.ordenier.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

class RegisterTest extends AbstractIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";
	private static final String PASSWORD = "clave-segura-123";

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void validRegistrationReturns201WithIdEmailAndValidTokenWithoutPassword() throws Exception {
		MvcResult result = register("ana@example.com", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresInSeconds").value(3600))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain(PASSWORD);
		Number id = JsonPath.read(body, "$.id");
		String token = JsonPath.read(body, "$.accessToken");
		Jwt jwt = jwtDecoder.decode(token);
		assertThat(jwt.getSubject()).isEqualTo(String.valueOf(id.longValue()));
	}

	@Test
	void storedPasswordHashIsBCryptAndNotThePlainPassword() throws Exception {
		register("ana@example.com", PASSWORD).andExpect(status().isCreated());

		String hash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE email = ?", String.class, "ana@example.com");

		assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
		assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
	}

	@Test
	void emailIsTrimmedAndLowercasedBeforeSavingAndInTheResponse() throws Exception {
		register("  Ana@Example.COM ", PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("ana@example.com"));

		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT email FROM users");
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).get("email")).isEqualTo("ana@example.com");
	}

	@Test
	void registeringTheSameEmailTwiceReturns409() throws Exception {
		register("ana@example.com", PASSWORD).andExpect(status().isCreated());

		assertDuplicate(register("ana@example.com", PASSWORD));
	}

	@Test
	void registeringDuplicateEmailIgnoringCaseAndSurroundingSpacesReturns409() throws Exception {
		register("a@x.com", PASSWORD).andExpect(status().isCreated());

		assertDuplicate(register(" A@X.com ", PASSWORD));
	}

	private void assertDuplicate(org.springframework.test.web.servlet.ResultActions result) throws Exception {
		result.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("EMAIL_DUPLICADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	private org.springframework.test.web.servlet.ResultActions register(String email, String password)
			throws Exception {
		String json = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
		return mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(json));
	}
}
