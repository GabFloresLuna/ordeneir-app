package cl.floresluna.ordenier.budget;

import static cl.floresluna.ordenier.category.CategoryTestSupport.CATEGORIES;
import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * R28 / constitución art. 4: ningún usuario ve, usa, edita ni elimina las categorías propias ni el presupuesto de otro.
 * Todo responde igual que si no existieran, y los datos del dueño quedan intactos.
 */
class BudgetIsolationTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";
	private static final String BUDGETS = "/api/v1/presupuestos/";
	private static final String MOVEMENTS = "/api/v1/movimientos";
	private static final String DATE = "2026-11-10T12:00:00-03:00";
	private static final String ANA_BUDGET = "{\"amount\":500000,\"lines\":[{\"categoryId\":%d,\"amount\":200000},"
			+ "{\"categoryId\":%d,\"amount\":100000}]}";

	private TestUser ana;
	private TestUser bea;
	private long food;
	private long anaCategory;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		anaCategory = insertOwn(jdbcTemplate, ana.id(), "Café de Ana", "GASTO");
		saveBudget(ana, MONTH, ANA_BUDGET.formatted(food, anaCategory));
	}

	// --- Categorías ---

	@Test
	void otherUserDoesNotSeeAnasOwnCategoryInTheList() throws Exception {
		String body = mockMvc.perform(get(CATEGORIES).header("Authorization", bea.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(11))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("Café de Ana");
		mockMvc.perform(get(CATEGORIES).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(12));
	}

	@Test
	void bothUsersSeeTheElevenPredefinedCategories() throws Exception {
		for (TestUser user : new TestUser[] {ana, bea}) {
			String body = mockMvc.perform(get(CATEGORIES).header("Authorization", user.bearer()))
					.andExpect(status().isOk())
					.andReturn().getResponse().getContentAsString();
			List<Object> predefined = JsonPath.read(body, "$[?(@.predefined == true)]");

			assertThat(predefined).hasSize(11);
		}
	}

	@Test
	void otherUserCannotEditAnasCategoryAndGetsSame404AsNonexistent() throws Exception {
		String foreign = categoryNotFound(mockMvc.perform(put(CATEGORIES + "/" + anaCategory)
				.header("Authorization", bea.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hackeada\"}")));
		String nonexistent = categoryNotFound(mockMvc.perform(put(CATEGORIES + "/999999")
				.header("Authorization", bea.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Hackeada\"}")));

		assertThat(foreign).isEqualTo(nonexistent);
		assertAnaDataIntact();
	}

	@Test
	void otherUserCannotDeleteAnasCategoryAndGetsSame404AsNonexistent() throws Exception {
		String foreign = categoryNotFound(mockMvc.perform(delete(CATEGORIES + "/" + anaCategory)
				.header("Authorization", bea.bearer())));
		String nonexistent = categoryNotFound(mockMvc.perform(delete(CATEGORIES + "/999999")
				.header("Authorization", bea.bearer())));

		assertThat(foreign).isEqualTo(nonexistent);
		assertAnaDataIntact();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM budget_lines WHERE category_id = ?", Long.class, anaCategory)).isEqualTo(1L);
	}

	@Test
	void otherUserCannotUseAnasCategoryInAMovementAndGetsSame400AsNonexistent() throws Exception {
		String foreign = invalidCategory(postMovement(bea, anaCategory));
		String nonexistent = invalidCategory(postMovement(bea, 999999));

		assertThat(foreign).isEqualTo(nonexistent);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Long.class)).isZero();
	}

	// --- Presupuesto ---

	@Test
	void otherUserCannotUseAnasCategoryInABudgetLineAndSavesNothing() throws Exception {
		String foreign = invalidCategory(putBudget(bea, MONTH,
				"{\"amount\":300000,\"lines\":[{\"categoryId\":" + anaCategory + ",\"amount\":100000}]}"));
		String nonexistent = invalidCategory(putBudget(bea, MONTH,
				"{\"amount\":300000,\"lines\":[{\"categoryId\":999999,\"amount\":100000}]}"));

		assertThat(foreign).isEqualTo(nonexistent);
		assertThat(budgetCountOf(bea)).isZero();
		assertAnaDataIntact();
	}

	@Test
	void otherUserDoesNotSeeAnasBudgetAndGetsSame404AsAMonthWithoutBudget() throws Exception {
		String foreign = budgetNotFound(mockMvc.perform(get(BUDGETS + MONTH)
				.header("Authorization", bea.bearer())));
		String withoutBudget = budgetNotFound(mockMvc.perform(get(BUDGETS + "2026-01")
				.header("Authorization", bea.bearer())));

		assertThat(foreign).isEqualTo(withoutBudget);
		assertThat(foreign).doesNotContain("500000");
	}

	@Test
	void otherUserCannotDeleteAnasBudgetAndItStaysIntact() throws Exception {
		budgetNotFound(mockMvc.perform(delete(BUDGETS + MONTH).header("Authorization", bea.bearer())));

		assertAnaDataIntact();
	}

	@Test
	void otherUserSavingTheSameMonthDoesNotTouchAnasBudget() throws Exception {
		putBudget(bea, MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(300000))
				.andExpect(jsonPath("$.lines.length()").value(1));

		assertAnaDataIntact();
		assertThat(budgetCountOf(bea)).isEqualTo(1L);
	}

	@Test
	void deletingOwnBudgetDoesNotAffectTheOtherUsersBudget() throws Exception {
		putBudget(bea, MONTH, "{\"amount\":300000}").andExpect(status().isOk());

		mockMvc.perform(delete(BUDGETS + MONTH).header("Authorization", bea.bearer()))
				.andExpect(status().isNoContent());

		assertAnaDataIntact();
	}

	@Test
	void bothUsersCanUsePredefinedCategoriesInTheirBudgets() throws Exception {
		putBudget(bea, MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":100000}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines[0].categoryId").value(food));
		assertAnaDataIntact();
	}

	// --- Dueño tomado del JWT (el cuerpo se ignora) ---

	@Test
	void creatingACategoryWithForeignUserIdOrIdInTheBodyKeepsTheTokenOwnerAndANewId() throws Exception {
		String response = mockMvc.perform(post(CATEGORIES).header("Authorization", bea.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Mía\",\"type\":\"GASTO\",\"id\":" + anaCategory + ",\"userId\":"
								+ ana.id() + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long newId = ((Number) JsonPath.read(response, "$.id")).longValue();

		assertThat(newId).isNotEqualTo(anaCategory);
		assertThat(categoryRow(newId).get("user_id")).isEqualTo(bea.id());
		assertAnaDataIntact();
	}

	@Test
	void editingOwnCategoryWithForeignUserIdOrIdInTheBodyKeepsOwnerAndId() throws Exception {
		long beaCategory = insertOwn(jdbcTemplate, bea.id(), "Cine de Bea", "GASTO");

		mockMvc.perform(put(CATEGORIES + "/" + beaCategory).header("Authorization", bea.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Teatro\",\"id\":" + anaCategory + ",\"userId\":" + ana.id() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(beaCategory))
				.andExpect(jsonPath("$.name").value("Teatro"));

		assertThat(categoryRow(beaCategory).get("user_id")).isEqualTo(bea.id());
		assertAnaDataIntact();
	}

	@Test
	void savingABudgetWithForeignUserIdOrIdInTheBodyKeepsTheTokenOwner() throws Exception {
		long anaBudgetId = jdbcTemplate.queryForObject(
				"SELECT id FROM budgets WHERE user_id = ?", Long.class, ana.id());

		putBudget(bea, MONTH, "{\"id\":" + anaBudgetId + ",\"userId\":" + ana.id() + ",\"amount\":300000}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(300000));

		assertThat(budgetCountOf(bea)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT id FROM budgets WHERE user_id = ?", Long.class, bea.id())).isNotEqualTo(anaBudgetId);
		assertAnaDataIntact();
	}

	// --- Ayudas ---

	/** La categoría y el presupuesto de Ana siguen exactamente como se crearon. */
	private void assertAnaDataIntact() throws Exception {
		Map<String, Object> row = categoryRow(anaCategory);
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("name")).isEqualTo("Café de Ana");
		assertThat(row.get("deleted_at")).isNull();

		mockMvc.perform(get(BUDGETS + MONTH).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(500000))
				.andExpect(jsonPath("$.lines.length()").value(2));
	}

	private Map<String, Object> categoryRow(long id) {
		return jdbcTemplate.queryForMap(
				"SELECT user_id, name, type, deleted_at FROM categories WHERE id = ?", id);
	}

	private long budgetCountOf(TestUser user) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM budgets WHERE user_id = ?", Long.class, user.id());
	}

	private ResultActions postMovement(TestUser user, long categoryId) throws Exception {
		return mockMvc.perform(post(MOVEMENTS).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":" + categoryId + ",\"amount\":1000,\"type\":\"GASTO\",\"occurredAt\":\""
						+ DATE + "\"}"));
	}

	private ResultActions putBudget(TestUser user, String month, String body) throws Exception {
		return mockMvc.perform(put(BUDGETS + month).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void saveBudget(TestUser user, String month, String body) throws Exception {
		putBudget(user, month, body).andExpect(status().isOk());
	}

	private String categoryNotFound(ResultActions result) throws Exception {
		return error(result, 404, "CATEGORIA_NO_ENCONTRADA");
	}

	private String budgetNotFound(ResultActions result) throws Exception {
		return error(result, 404, "PRESUPUESTO_NO_ENCONTRADO");
	}

	private String invalidCategory(ResultActions result) throws Exception {
		return error(result, 400, "CATEGORIA_INVALIDA");
	}

	private String error(ResultActions result, int httpStatus, String code) throws Exception {
		return result.andExpect(status().is(httpStatus))
				.andExpect(jsonPath("$.codigo").value(code))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
	}
}
