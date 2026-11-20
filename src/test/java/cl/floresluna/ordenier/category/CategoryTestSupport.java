package cl.floresluna.ordenier.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Ayudas de prueba para categorías: ids de predefinidas y categorías propias por SQL (plan, estrategia de tests). */
public final class CategoryTestSupport {

	public static final String CATEGORIES = "/api/v1/categorias";

	private CategoryTestSupport() {
	}

	public record TestUser(long id, String token) {

		public String bearer() {
			return "Bearer " + token;
		}
	}

	/** Registra un usuario por la API real y entrega su id y token. */
	public static TestUser registerUser(MockMvc mockMvc, String email) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"clave-segura-123\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new TestUser(((Number) JsonPath.read(body, "$.id")).longValue(),
				JsonPath.read(body, "$.accessToken"));
	}

	public static long predefinedId(JdbcTemplate jdbc, String name, String type) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE user_id IS NULL AND name = ? AND type = ?",
				Long.class, name, type);
	}

	public static long othersId(JdbcTemplate jdbc, String type) {
		return predefinedId(jdbc, "Otros", type);
	}

	/** Fragmento JSON `"categoryId":N,` con "Otros" del tipo dado; para un tipo inválido usa el de GASTO. */
	public static String categoryField(JdbcTemplate jdbc, String type) {
		String validType = "INGRESO".equals(type) ? "INGRESO" : "GASTO";
		return "\"categoryId\":" + othersId(jdbc, validType) + ",";
	}

	public static long insertOwn(JdbcTemplate jdbc, long userId, String name, String type) {
		return jdbc.queryForObject(
				"INSERT INTO categories (user_id, name, type) VALUES (?, ?, ?) RETURNING id",
				Long.class, userId, name, type);
	}

	public static void markDeleted(JdbcTemplate jdbc, long categoryId) {
		jdbc.update("UPDATE categories SET deleted_at = now() WHERE id = ?", categoryId);
	}
}
