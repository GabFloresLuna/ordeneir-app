package cl.floresluna.ordenier.category;

import static cl.floresluna.ordenier.category.CategoryTestSupport.CATEGORIES;
import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.markDeleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R2, R4, R5, R6: crear categorías propias. */
class CategoryCreateTest extends AbstractIntegrationTest {

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void validCreateReturns201WithOwnCategoryDataAndTokenOwner() throws Exception {
		send(ana, body("Mascotas", "GASTO"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("Mascotas"))
				.andExpect(jsonPath("$.type").value("GASTO"))
				.andExpect(jsonPath("$.predefined").value(false))
				.andExpect(jsonPath("$.userId").doesNotExist());

		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT user_id, name, type, deleted_at FROM categories WHERE user_id IS NOT NULL");
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("name")).isEqualTo("Mascotas");
		assertThat(row.get("type")).isEqualTo("GASTO");
		assertThat(row.get("deleted_at")).isNull();
	}

	@Test
	void validCreateOfIngresoTypeReturns201() throws Exception {
		send(ana, body("Bonos", "INGRESO"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("INGRESO"));
	}

	@Test
	void createdCategoryAppearsInTheOwnersListButNotInAnotherUsers() throws Exception {
		String response = send(ana, body("Mascotas", "GASTO")).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(response, "$.id")).longValue();

		String anaList = mockMvc.perform(get(CATEGORIES).header("Authorization", ana.bearer()))
				.andReturn().getResponse().getContentAsString();
		String beaList = mockMvc.perform(get(CATEGORIES).header("Authorization", bea.bearer()))
				.andReturn().getResponse().getContentAsString();

		assertThat((List<Number>) JsonPath.read(anaList, "$[*].id")).extracting(Number::longValue).contains(id);
		assertThat((List<Number>) JsonPath.read(beaList, "$[*].id")).extracting(Number::longValue)
				.doesNotContain(id);
	}

	@Test
	void nameIsTrimmedBeforeSaving() throws Exception {
		send(ana, body("   Mascotas  ", "GASTO"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Mascotas"));
	}

	@Test
	void bodyWithIdUserIdOrPredefinedIsIgnored() throws Exception {
		String response = send(ana, "{\"id\":1,\"userId\":" + bea.id()
				+ ",\"predefined\":true,\"name\":\"Mascotas\",\"type\":\"GASTO\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.predefined").value(false))
				.andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(response, "$.id")).longValue();

		assertThat(id).isNotEqualTo(1L);
		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT user_id FROM categories WHERE id = ?", id);
		assertThat(row.get("user_id")).isEqualTo(ana.id());
	}

	@Test
	void nameOfExactlyFiftyCharactersIsAcceptedAndFiftyOneIsRejected() throws Exception {
		send(ana, body("a".repeat(50), "GASTO")).andExpect(status().isCreated());
		assertInvalidData(body("b".repeat(51), "GASTO"));
	}

	@Test
	void nameLengthIsCountedInCodePointsNotUtf16Units() throws Exception {
		send(ana, body("😀".repeat(50), "GASTO")).andExpect(status().isCreated());
		assertInvalidData(body("😀".repeat(51), "GASTO"));
	}

	@Test
	void invalidBodiesReturn400DatosInvalidosAndCreateNothing() throws Exception {
		List<String> bodies = List.of(
				"{\"type\":\"GASTO\"}",
				"{\"name\":null,\"type\":\"GASTO\"}",
				body("", "GASTO"),
				body("   ", "GASTO"),
				"{\"name\":123,\"type\":\"GASTO\"}",
				"{\"name\":[\"Mascotas\"],\"type\":\"GASTO\"}",
				"{\"name\":\"Mascotas\"}",
				"{\"name\":\"Mascotas\",\"type\":null}",
				body("Mascotas", "OTRO"),
				body("Mascotas", "gasto"),
				"{\"name\":\"Mascotas\",\"type\":5}",
				"{}");

		for (String invalid : bodies) {
			assertInvalidData(invalid);
		}
		assertThat(ownCategoryCount()).isZero();
	}

	@Test
	void duplicateOwnNameWithOtherCaseReturns409() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, body("MASCOTAS", "GASTO"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
		assertThat(ownCategoryCount()).isEqualTo(1);
	}

	@Test
	void duplicateNameWithSurroundingSpacesReturns409() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, body("  mascotas ", "GASTO"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"));
	}

	@Test
	void nameEqualToAPredefinedOfTheSameTypeReturns409() throws Exception {
		send(ana, body("otros", "GASTO"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"));
		send(ana, body("Sueldo", "INGRESO"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_DUPLICADA"));
		assertThat(ownCategoryCount()).isZero();
	}

	@Test
	void duplicateErrorMessageDoesNotRepeatTheSentName() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		String response = send(ana, body("mascotas", "GASTO")).andExpect(status().isConflict())
				.andReturn().getResponse().getContentAsString();

		assertThat(response).doesNotContain("mascotas").doesNotContain("Mascotas");
	}

	@Test
	void sameNameInTheOtherTypeIsValid() throws Exception {
		send(ana, body("Sueldo", "GASTO")).andExpect(status().isCreated());
		send(ana, body("Mascotas", "GASTO")).andExpect(status().isCreated());
		send(ana, body("Mascotas", "INGRESO")).andExpect(status().isCreated());
	}

	@Test
	void sameNameInAnotherUserIsValid() throws Exception {
		insertOwn(jdbcTemplate, bea.id(), "Mascotas", "GASTO");

		send(ana, body("Mascotas", "GASTO")).andExpect(status().isCreated());
	}

	@Test
	void nameOfADeletedOwnCategoryCanBeUsedAgain() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		markDeleted(jdbcTemplate, id);

		send(ana, body("Mascotas", "GASTO")).andExpect(status().isCreated());
	}

	@Test
	void databaseRejectsADuplicateNameEvenWithoutTheServiceCheck() {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		assertThatThrownBy(() -> insertOwn(jdbcTemplate, ana.id(), "mascotas", "GASTO"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void createWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post(CATEGORIES).contentType(MediaType.APPLICATION_JSON)
						.content(body("Mascotas", "GASTO")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private void assertInvalidData(String body) throws Exception {
		send(ana, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	private ResultActions send(TestUser user, String body) throws Exception {
		return mockMvc.perform(post(CATEGORIES).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private int ownCategoryCount() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM categories WHERE user_id IS NOT NULL",
				Integer.class);
	}

	private static String body(String name, String type) {
		return "{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}";
	}
}
