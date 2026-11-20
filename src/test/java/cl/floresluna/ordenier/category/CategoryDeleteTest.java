package cl.floresluna.ordenier.category;

import static cl.floresluna.ordenier.category.CategoryTestSupport.CATEGORIES;
import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.markDeleted;
import static cl.floresluna.ordenier.category.CategoryTestSupport.othersId;
import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R9 (eliminar), R10 (eliminar), R11, R12: borrado lógico de categorías propias. */
class CategoryDeleteTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void deletingAnOwnCategoryWithoutMovementsReturns204AndKeepsTheRowMarkedAsDeleted() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		remove(ana, id).andExpect(status().isNoContent()).andExpect(content().string(""));

		assertThat(rowCount(id)).isEqualTo(1);
		assertThat(isDeleted(id)).isTrue();
	}

	@Test
	void aDeletedCategoryNoLongerAppearsInTheList() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		insertOwn(jdbcTemplate, ana.id(), "Viajes", "GASTO");

		remove(ana, id).andExpect(status().isNoContent());

		mockMvc.perform(get(CATEGORIES).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Mascotas')]").isEmpty())
				.andExpect(jsonPath("$[?(@.name == 'Viajes')]").isNotEmpty());
	}

	@Test
	void deletingTheSameCategoryTwiceReturns404TheSecondTime() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		remove(ana, id).andExpect(status().isNoContent());
		remove(ana, id).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_ENCONTRADA"));
	}

	@Test
	void deletingACategoryDiscardsItsBudgetLinesAndKeepsTheRest() throws Exception {
		long pets = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		long food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		saveBudget(ana, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + pets + ",\"amount\":100000},"
				+ "{\"categoryId\":" + food + ",\"amount\":200000}]}");
		assertThat(lineCount(pets)).isEqualTo(1);

		remove(ana, pets).andExpect(status().isNoContent());

		assertThat(lineCount(pets)).isZero();
		assertThat(lineCount(food)).isEqualTo(1);
		mockMvc.perform(get("/api/v1/presupuestos/" + MONTH).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(500000))
				.andExpect(jsonPath("$.lines.length()").value(1))
				.andExpect(jsonPath("$.lines[0].categoryId").value(food));
	}

	@Test
	void aCategoryWithAMovementReturns409AndIsNotDeleted() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		saveBudget(ana, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + id + ",\"amount\":100000}]}");
		insertMovement(ana, "GASTO", id, false);

		remove(ana, id)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_CON_MOVIMIENTOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());

		assertThat(isDeleted(id)).isFalse();
		assertThat(lineCount(id)).isEqualTo(1);
	}

	@Test
	void aCategoryWithOneActiveAndOneDeletedMovementStillReturns409() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		insertMovement(ana, "GASTO", id, true);
		insertMovement(ana, "GASTO", id, false);

		remove(ana, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_CON_MOVIMIENTOS"));
		assertThat(isDeleted(id)).isFalse();
	}

	@Test
	void aCategoryWithOnlyDeletedMovementsCanBeDeleted() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		insertMovement(ana, "GASTO", id, true);

		remove(ana, id).andExpect(status().isNoContent());
		assertThat(isDeleted(id)).isTrue();
	}

	@Test
	void anIncomeCategoryWithAMovementReturns409() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Bonos", "INGRESO");
		insertMovement(ana, "INGRESO", id, false);

		remove(ana, id).andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_CON_MOVIMIENTOS"));
		assertThat(isDeleted(id)).isFalse();
	}

	@Test
	void deletingAPredefinedCategoryReturns403AndChangesNothing() throws Exception {
		long otros = othersId(jdbcTemplate, "GASTO");

		remove(ana, otros)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_MODIFICABLE"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());

		assertThat(isDeleted(otros)).isFalse();
	}

	@Test
	void missingDeletedAndForeignCategoriesReturn404WithIdenticalBody() throws Exception {
		long deleted = insertOwn(jdbcTemplate, ana.id(), "Vieja", "GASTO");
		markDeleted(jdbcTemplate, deleted);
		long foreign = insertOwn(jdbcTemplate, bea.id(), "De Bea", "GASTO");
		insertMovement(bea, "GASTO", foreign, false);

		String missingBody = remove(ana, 999_999L).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("CATEGORIA_NO_ENCONTRADA"))
				.andReturn().getResponse().getContentAsString();
		String deletedBody = remove(ana, deleted).andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		String foreignBody = remove(ana, foreign).andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();

		assertThat(missingBody).isNotEmpty().isEqualTo(deletedBody).isEqualTo(foreignBody);
		assertThat(isDeleted(foreign)).isFalse();
	}

	@Test
	void deletingWithoutTokenReturns401() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		mockMvc.perform(delete(CATEGORIES + "/" + id))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
		assertThat(isDeleted(id)).isFalse();
	}

	private ResultActions remove(TestUser user, long id) throws Exception {
		return mockMvc.perform(delete(CATEGORIES + "/" + id).header("Authorization", user.bearer()));
	}

	private void saveBudget(TestUser user, String body) throws Exception {
		mockMvc.perform(put("/api/v1/presupuestos/" + MONTH).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
	}

	private void insertMovement(TestUser user, String type, long categoryId, boolean deleted) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at, deleted_at) "
				+ "VALUES (?, 10000, ?, ?, '2026-11-10T12:00:00Z'::timestamptz, "
				+ "CASE WHEN ? THEN now() ELSE NULL END)", user.id(), type, categoryId, deleted);
	}

	private boolean isDeleted(long id) {
		return jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM categories WHERE id = ?",
				Boolean.class, id);
	}

	private int rowCount(long id) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM categories WHERE id = ?", Integer.class, id);
	}

	private int lineCount(long categoryId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM budget_lines WHERE category_id = ?",
				Integer.class, categoryId);
	}
}
