package cl.floresluna.ordenier.summary;

import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R10 (constitución, art. 6): cada usuario ve solo su resumen; el del otro queda intacto. */
class SummaryIsolationTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private TestUser beto;
	private long food;
	private long anaOwn;
	private long betoOwn;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		beto = CategoryTestSupport.registerUser(mockMvc, "beto@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		anaOwn = insertOwn(jdbcTemplate, ana.id(), "Mascotas de Ana", "GASTO");
		betoOwn = insertOwn(jdbcTemplate, beto.id(), "Hobbies de Beto", "GASTO");

		insertMovement(ana, "GASTO", food, 40_000);
		insertMovement(ana, "GASTO", anaOwn, 15_000);
		insertMovement(ana, "INGRESO", predefinedId(jdbcTemplate, "Sueldo", "INGRESO"), 900_000);
		insertMovement(beto, "GASTO", food, 7_000);
		insertMovement(beto, "GASTO", betoOwn, 25_000);

		saveBudget(ana, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + anaOwn + ",\"amount\":20000}]}");
		saveBudget(beto, "{\"amount\":100000,\"lines\":[{\"categoryId\":" + betoOwn + ",\"amount\":10000}]}");
	}

	@Test
	void eachUserSeesOnlyTheirOwnTotalsCategoriesAndBudget() throws Exception {
		read(ana).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(900000))
				.andExpect(jsonPath("$.totalExpenses").value(55000))
				.andExpect(jsonPath("$.balance").value(845000))
				.andExpect(jsonPath("$.expensesByCategory.length()").value(2))
				.andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(food))
				.andExpect(jsonPath("$.expensesByCategory[0].total").value(40000))
				.andExpect(jsonPath("$.expensesByCategory[1].categoryId").value(anaOwn))
				.andExpect(jsonPath("$.expensesByCategory[1].name").value("Mascotas de Ana"))
				.andExpect(jsonPath("$.budget.amount").value(300000))
				.andExpect(jsonPath("$.budget.spent").value(55000))
				.andExpect(jsonPath("$.budget.lines.length()").value(1))
				.andExpect(jsonPath("$.budget.lines[0].categoryId").value(anaOwn));

		read(beto).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.totalExpenses").value(32000))
				.andExpect(jsonPath("$.balance").value(-32000))
				.andExpect(jsonPath("$.incomeByCategory.length()").value(0))
				.andExpect(jsonPath("$.expensesByCategory.length()").value(2))
				.andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(betoOwn))
				.andExpect(jsonPath("$.expensesByCategory[0].name").value("Hobbies de Beto"))
				.andExpect(jsonPath("$.expensesByCategory[1].categoryId").value(food))
				.andExpect(jsonPath("$.expensesByCategory[1].total").value(7000))
				.andExpect(jsonPath("$.budget.amount").value(100000))
				.andExpect(jsonPath("$.budget.spent").value(32000))
				.andExpect(jsonPath("$.budget.lines[0].categoryId").value(betoOwn));
	}

	@Test
	void aUserWithoutDataSeesAnEmptySummaryEvenIfOthersHaveData() throws Exception {
		TestUser carla = CategoryTestSupport.registerUser(mockMvc, "carla@example.com");

		read(carla).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.totalExpenses").value(0))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.expensesByCategory.length()").value(0))
				.andExpect(jsonPath("$.incomeByCategory.length()").value(0))
				.andExpect(jsonPath("$.budget").doesNotExist());
	}

	@Test
	void readingOneSummaryLeavesTheOtherUsersSummaryUntouched() throws Exception {
		String before = read(beto).andReturn().getResponse().getContentAsString();

		read(ana).andExpect(status().isOk());
		Long movements = jdbcTemplate.queryForObject("SELECT count(*) FROM movements WHERE user_id = ?",
				Long.class, beto.id());
		String after = read(beto).andReturn().getResponse().getContentAsString();

		assertThat(movements).isEqualTo(2L);
		assertThat(after).isEqualTo(before);
	}

	private ResultActions read(TestUser user) throws Exception {
		return mockMvc.perform(get("/api/v1/resumenes/" + MONTH).header("Authorization", user.bearer()));
	}

	private void saveBudget(TestUser user, String body) throws Exception {
		mockMvc.perform(put("/api/v1/presupuestos/" + MONTH).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
	}

	private void insertMovement(TestUser user, String type, long categoryId, long amount) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at) "
				+ "VALUES (?, ?, ?, ?, ?::timestamptz)", user.id(), amount, type, categoryId,
				"2026-11-10T12:00:00Z");
	}
}
