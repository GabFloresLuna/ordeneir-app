package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.category.CategoryTestSupport.categoryField;
import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.markDeleted;
import static cl.floresluna.ordenier.category.CategoryTestSupport.othersId;
import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static cl.floresluna.ordenier.movement.MovementTestSupport.MOVEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.movement.MovementTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MovementUpdateTest extends AbstractIntegrationTest {

	private static final String ORIGINAL_DATE = "2026-11-19T15:30:00-03:00";
	private static final String NEW_DATE = "2026-11-18T10:00:00-03:00";

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void validUpdateReturns200WithNewValuesAndSameIdAndOwner() throws Exception {
		long id = createFor(ana, "Almuerzo");

		update(ana, id, body(250000, "INGRESO", NEW_DATE, "Sueldo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.amount").value(250000))
				.andExpect(jsonPath("$.type").value("INGRESO"))
				.andExpect(jsonPath("$.categoryId").value(othersId(jdbcTemplate, "INGRESO")))
				.andExpect(jsonPath("$.occurredAt").value("2026-11-18T13:00:00Z"))
				.andExpect(jsonPath("$.description").value("Sueldo"))
				.andExpect(jsonPath("$.userId").doesNotExist());

		Map<String, Object> row = row(id);
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("amount")).isEqualTo(250000L);
		assertThat(row.get("type")).isEqualTo("INGRESO");
		assertThat(row.get("description")).isEqualTo("Sueldo");
	}

	@Test
	void updateWithoutDescriptionLeavesItNull() throws Exception {
		long id = createFor(ana, "Almuerzo");

		update(ana, id, "{" + cat("GASTO") + "\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":\"" + NEW_DATE + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));

		assertThat(row(id).get("description")).isNull();
	}

	@Test
	void bodyWithIdOrUserIdDoesNotChangeIdOrOwner() throws Exception {
		long id = createFor(ana, "Almuerzo");

		update(ana, id, "{" + cat("GASTO") + "\"id\":999,\"userId\":" + bea.id() + ",\"amount\":500,\"type\":\"GASTO\","
				+ "\"occurredAt\":\"" + NEW_DATE + "\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));

		assertThat(row(id).get("user_id")).isEqualTo(ana.id());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Long.class)).isEqualTo(1L);
	}

	@Test
	void dateUpToOneMinuteAheadIsAcceptedOnUpdate() throws Exception {
		long id = createFor(ana, "Almuerzo");
		String soon = Instant.now().plus(1, ChronoUnit.MINUTES).toString();

		update(ana, id, body(100, "GASTO", soon, null)).andExpect(status().isOk());
	}

	@Test
	void invalidDataReturns400AndLeavesMovementIntact() throws Exception {
		long id = createFor(ana, "Almuerzo");
		Map<String, Object> before = row(id);
		String future = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
		String tooLong = "x".repeat(201);

		List<String> bodies = List.of(
				"{" + cat("GASTO") + "\"type\":\"GASTO\",\"occurredAt\":\"" + NEW_DATE + "\"}",
				"{" + cat("GASTO") + "\"amount\":100,\"occurredAt\":\"" + NEW_DATE + "\"}",
				"{" + cat("GASTO") + "\"amount\":100,\"type\":\"GASTO\"}",
				body(0, "GASTO", NEW_DATE, null),
				body(-1, "GASTO", NEW_DATE, null),
				"{" + cat("GASTO") + "\"amount\":10.5,\"type\":\"GASTO\",\"occurredAt\":\"" + NEW_DATE + "\"}",
				"{" + cat("GASTO") + "\"amount\":\"100\",\"type\":\"GASTO\",\"occurredAt\":\"" + NEW_DATE + "\"}",
				body(1_000_000_000_001L, "GASTO", NEW_DATE, null),
				body(100, "ingreso", NEW_DATE, null),
				body(100, "OTRO", NEW_DATE, null),
				body(100, "GASTO", "2026-11-18T10:00:00", null),
				body(100, "GASTO", "2026-11-18", null),
				body(100, "GASTO", future, null),
				body(100, "GASTO", NEW_DATE, tooLong),
				"{" + cat("GASTO") + "\"amount\":100,");

		for (String invalid : bodies) {
			update(ana, id, invalid)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}

		assertThat(row(id)).isEqualTo(before);
	}

	@Test
	void nonNumericIdReturns400DatosInvalidos() throws Exception {
		mockMvc.perform(put(MOVEMENTS + "/abc").header("Authorization", ana.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(100, "GASTO", NEW_DATE, null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void updatingNonexistentDeletedAndOtherUsersMovementReturnsIdentical404() throws Exception {
		long deletedId = createFor(ana, "Eliminado");
		jdbcTemplate.update("UPDATE movements SET deleted_at = now() WHERE id = ?", deletedId);
		long othersId = createFor(bea, "Ajeno");
		Map<String, Object> othersBefore = row(othersId);
		String valid = body(777, "INGRESO", NEW_DATE, "Cambiado");

		String nonexistent = notFoundBody(update(ana, 999999L, valid));
		String deleted = notFoundBody(update(ana, deletedId, valid));
		String other = notFoundBody(update(ana, othersId, valid));

		assertThat(deleted).isEqualTo(nonexistent);
		assertThat(other).isEqualTo(nonexistent);
		assertThat((String) JsonPath.read(nonexistent, "$.codigo")).isEqualTo("MOVIMIENTO_NO_ENCONTRADO");
		assertThat(row(othersId)).isEqualTo(othersBefore);
		assertThat(row(deletedId).get("amount")).isEqualTo(15000L);
	}

	@Test
	void updatingWithoutTokenReturns401() throws Exception {
		mockMvc.perform(put(MOVEMENTS + "/1").contentType(MediaType.APPLICATION_JSON)
						.content(body(100, "GASTO", NEW_DATE, null)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private long createFor(TestUser user, String description) throws Exception {
		String response = mockMvc.perform(post(MOVEMENTS).header("Authorization", user.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(15000, "GASTO", ORIGINAL_DATE, description)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private ResultActions update(TestUser user, long id, String json) throws Exception {
		return mockMvc.perform(put(MOVEMENTS + "/" + id).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private Map<String, Object> row(long id) {
		return jdbcTemplate.queryForMap(
				"SELECT user_id, amount, type, category_id, occurred_at, description, deleted_at FROM movements "
						+ "WHERE id = ?", id);
	}

	private String notFoundBody(ResultActions result) throws Exception {
		return result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("MOVIMIENTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
	}

	private String body(long amount, String type, String occurredAt, String description) {
		String json = "{" + cat(type) + "\"amount\":" + amount + ",\"type\":\"" + type + "\",\"occurredAt\":\"" + occurredAt + "\"";
		if (description != null) {
			json += ",\"description\":\"" + description + "\"";
		}
		return json + "}";
	}

	@Test
	void updateChangesTheCategoryToAnotherOfTheSameType() throws Exception {
		long id = createFor(ana, "Almuerzo");
		long food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		long own = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		for (long categoryId : List.of(food, own)) {
			update(ana, id, withCategory(categoryId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.categoryId").value(categoryId));
			assertThat(row(id).get("category_id")).isEqualTo(categoryId);
		}
	}

	@Test
	void missingOrNonIntegerCategoryIdReturns400DatosInvalidosAndLeavesMovementIntact() throws Exception {
		long id = createFor(ana, "Almuerzo");
		Map<String, Object> before = row(id);

		update(ana, id, "{\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":\"" + NEW_DATE + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		for (String value : List.of("null", "\"5\"", "1.5", "true", "[]")) {
			update(ana, id, "{\"categoryId\":" + value + ",\"amount\":100,\"type\":\"GASTO\","
					+ "\"occurredAt\":\"" + NEW_DATE + "\"}")
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}

		assertThat(row(id)).isEqualTo(before);
	}

	@Test
	void unknownDeletedForeignOrWrongTypeCategoryReturns400CategoriaInvalidaAndLeavesMovementIntact()
			throws Exception {
		long id = createFor(ana, "Almuerzo");
		Map<String, Object> before = row(id);
		long foreign = insertOwn(jdbcTemplate, bea.id(), "De Bea", "GASTO");
		long deleted = insertOwn(jdbcTemplate, ana.id(), "Borrada", "GASTO");
		markDeleted(jdbcTemplate, deleted);
		long wrongType = othersId(jdbcTemplate, "INGRESO");

		List<String> bodies = new ArrayList<>();
		for (long categoryId : List.of(999_999L, -1L, foreign, deleted, wrongType)) {
			bodies.add(update(ana, id, withCategory(categoryId))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("CATEGORIA_INVALIDA"))
					.andExpect(jsonPath("$.mensaje").isNotEmpty())
					.andReturn().getResponse().getContentAsString());
		}

		assertThat(bodies).allMatch(bodies.get(0)::equals);
		assertThat(row(id)).isEqualTo(before);
	}

	private static String withCategory(long categoryId) {
		return "{\"categoryId\":" + categoryId + ",\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":\""
				+ NEW_DATE + "\"}";
	}

	private String cat(String type) {
		return categoryField(jdbcTemplate, type);
	}
}
