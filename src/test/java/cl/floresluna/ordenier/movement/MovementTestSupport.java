package cl.floresluna.ordenier.movement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registra usuarios por la API real y entrega su id y token (plan D16). */
final class MovementTestSupport {

	static final String MOVEMENTS = "/api/v1/movimientos";

	private MovementTestSupport() {
	}

	record TestUser(long id, String token) {

		String bearer() {
			return "Bearer " + token;
		}
	}

	static TestUser registerUser(MockMvc mockMvc, String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"clave-segura-123\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new TestUser(((Number) JsonPath.read(body, "$.id")).longValue(),
				JsonPath.read(body, "$.accessToken"));
	}
}
