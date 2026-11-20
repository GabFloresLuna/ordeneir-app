package cl.floresluna.ordenier.category;

import static cl.floresluna.ordenier.category.CategoryTestSupport.CATEGORIES;
import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.markDeleted;
import static cl.floresluna.ordenier.category.CategoryTestSupport.othersId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R3, R8, R9 (editar), R10 (editar): renombrar categorías propias. */
class CategoryUpdateTest extends AbstractIntegrationTest {

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void renamingAnOwnCategoryReturns200AndSavesTheNewName() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"Veterinario\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.name").value("Veterinario"))
				.andExpect(jsonPath("$.type").value("GASTO"))
				.andExpect(jsonPath("$.predefined").value(false))
				.andExpect(jsonPath("$.userId").doesNotExist());

		assertThat(nameOf(id)).isEqualTo("Veterinario");
	}

	@Test
	void nameIsTrimmedBeforeSaving() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"   Veterinario  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Veterinario"));
		assertThat(nameOf(id)).isEqualTo("Veterinario");
	}

	@Test
	void aTypeInTheBodyIsIgnoredAndTheTypeNeverChanges() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"Veterinario\",\"type\":\"INGRESO\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("GASTO"));
		send(ana, id, "{\"name\":\"Veterinaria\",\"type\":\"cualquier-cosa\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("GASTO"));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT name, type FROM categories WHERE id = ?", id);
		assertThat(row.get("type")).isEqualTo("GASTO");
		assertThat(row.get("name")).isEqualTo("Veterinaria");
	}

	@Test
	void bodyWithIdUserIdOrPredefinedIsIgnored() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"id\":1,\"userId\":" + bea.id() + ",\"predefined\":true,\"name\":\"Veterinario\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.predefined").value(false));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT user_id, name FROM categories WHERE id = ?", id);
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("name")).isEqualTo("Veterinario");
	}

	@Test
	void nameOfExactlyFiftyCharactersIsAcceptedAndFiftyOneIsRejected() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"" + "a".repeat(50) + "\"}").andExpect(status().isOk());
		assertInvalidData(id, "{\"name\":\"" + "b".repeat(51) + "\"}");
		assertThat(nameOf(id)).isEqualTo("a".repeat(50));
	}

	@Test
	void invalidNamesReturn400DatosInvalidosAndChangeNothing() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		List<String> bodies = List.of(
				"{}",
				"{\"name\":null}",
				"{\"name\":\"\"}",
				"{\"name\":\"   \"}",
				"{\"name\":123}",
				"{\"name\":[\"Veterinario\"]}",
				"{\"type\":\"GASTO\"}");

		for (String invalid : bodies) {
			assertInvalidData(id, invalid);
		}
		assertThat(nameOf(id)).isEqualTo("Mascotas");
	}

	@Test
	void duplicateOfAnotherOwnCategoryOfTheSameTypeReturns409() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Veterinario", "GASTO");
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"  VETERINARIO \"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
		assertThat(nameOf(id)).isEqualTo("Mascotas");
	}

	@Test
	void nameEqualToAPredefinedOfTheSameTypeReturns409() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"otros\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"));
		assertThat(nameOf(id)).isEqualTo("Mascotas");
	}

	@Test
	void renamingToANameOfAnotherTypeOrAnotherUserIsValid() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Bonos", "INGRESO");
		insertOwn(jdbcTemplate, bea.id(), "Veterinario", "GASTO");
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"Bonos\"}").andExpect(status().isOk());
		send(ana, id, "{\"name\":\"Veterinario\"}").andExpect(status().isOk());
	}

	@Test
	void nameOfADeletedOwnCategoryCanBeUsed() throws Exception {
		long deleted = insertOwn(jdbcTemplate, ana.id(), "Veterinario", "GASTO");
		markDeleted(jdbcTemplate, deleted);
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, id, "{\"name\":\"Veterinario\"}").andExpect(status().isOk());
	}

	@Test
	void renamingToItselfChangingOnlyTheCaseIsAllowed() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Café", "GASTO");

		send(ana, id, "{\"name\":\"café\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("café"));
		send(ana, id, "{\"name\":\"café\"}").andExpect(status().isOk());
		assertThat(nameOf(id)).isEqualTo("café");
	}

	@Test
	void editingAPredefinedCategoryReturns403AndChangesNothing() throws Exception {
		long otros = othersId(jdbcTemplate, "GASTO");

		send(ana, otros, "{\"name\":\"Varios\"}")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_MODIFICABLE"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
		assertThat(nameOf(otros)).isEqualTo("Otros");
	}

	@Test
	void missingDeletedAndForeignCategoriesReturn404WithIdenticalBody() throws Exception {
		long deleted = insertOwn(jdbcTemplate, ana.id(), "Vieja", "GASTO");
		markDeleted(jdbcTemplate, deleted);
		long foreign = insertOwn(jdbcTemplate, bea.id(), "De Bea", "GASTO");

		String missingBody = send(ana, 999_999L, "{\"name\":\"Nueva\"}").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_ENCONTRADA"))
				.andReturn().getResponse().getContentAsString();
		String deletedBody = send(ana, deleted, "{\"name\":\"Nueva\"}").andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		String foreignBody = send(ana, foreign, "{\"name\":\"Nueva\"}").andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();

		assertThat(missingBody).isNotEmpty().isEqualTo(deletedBody).isEqualTo(foreignBody);
		assertThat(nameOf(deleted)).isEqualTo("Vieja");
		assertThat(nameOf(foreign)).isEqualTo("De Bea");
	}

	@Test
	void updateWithoutTokenReturns401() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		mockMvc.perform(put(CATEGORIES + "/" + id).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Veterinario\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private void assertInvalidData(long id, String body) throws Exception {
		send(ana, id, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	private ResultActions send(TestUser user, long id, String body) throws Exception {
		return mockMvc.perform(put(CATEGORIES + "/" + id).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String nameOf(long id) {
		return jdbcTemplate.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, id);
	}
}
