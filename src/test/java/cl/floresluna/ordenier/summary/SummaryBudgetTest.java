package cl.floresluna.ordenier.summary;

import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R1, R4, R5, R6: presupuesto del mes dentro de GET /resumenes/{mes}. */
class SummaryBudgetTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private long food;
	private long transport;
	private long health;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		transport = predefinedId(jdbcTemplate, "Transporte", "GASTO");
		health = predefinedId(jdbcTemplate, "Salud", "GASTO");
	}

	@Test
	void withBudgetTheSummaryHoldsTheGeneralAmountAndEveryLine() throws Exception {
		saveBudget(MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":200000},"
				+ "{\"categoryId\":" + transport + ",\"amount\":100000}]}");
		insertExpense(food, 80_000, "2026-11-10T12:00:00Z");
		insertExpense(transport, 120_000, "2026-11-11T12:00:00Z");

		read(MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget.month").value(MONTH))
				.andExpect(jsonPath("$.budget.amount").value(500000))
				.andExpect(jsonPath("$.budget.spent").value(200000))
				.andExpect(jsonPath("$.budget.remaining").value(300000))
				.andExpect(jsonPath("$.budget.exceeded").value(false))
				.andExpect(jsonPath("$.budget.lines.length()").value(2))
				.andExpect(jsonPath("$.budget.lines[0].categoryId").value(food))
				.andExpect(jsonPath("$.budget.lines[0].amount").value(200000))
				.andExpect(jsonPath("$.budget.lines[0].spent").value(80000))
				.andExpect(jsonPath("$.budget.lines[0].remaining").value(120000))
				.andExpect(jsonPath("$.budget.lines[0].exceeded").value(false))
				.andExpect(jsonPath("$.budget.lines[1].categoryId").value(transport))
				.andExpect(jsonPath("$.budget.lines[1].spent").value(120000))
				.andExpect(jsonPath("$.budget.lines[1].remaining").value(-20000))
				.andExpect(jsonPath("$.budget.lines[1].exceeded").value(true));
	}

	@Test
	void withoutBudgetTheBudgetFieldIsNull() throws Exception {
		insertExpense(food, 10_000, "2026-11-10T12:00:00Z");

		read(MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget").doesNotExist())
				.andExpect(jsonPath("$.totalExpenses").value(10000));
	}

	@Test
	void aBudgetOfAnotherMonthDoesNotShowUp() throws Exception {
		saveBudget("2026-10", "{\"amount\":300000}");

		read(MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget").doesNotExist());
	}

	@Test
	void aLineWithoutSpendingShowsSpentZero() throws Exception {
		saveBudget(MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + health + ",\"amount\":50000}]}");

		read(MONTH)
				.andExpect(jsonPath("$.budget.lines.length()").value(1))
				.andExpect(jsonPath("$.budget.lines[0].spent").value(0))
				.andExpect(jsonPath("$.budget.lines[0].remaining").value(50000))
				.andExpect(jsonPath("$.budget.lines[0].exceeded").value(false));
	}

	@Test
	void spendingInACategoryWithoutALineCountsForTheGeneralBudget() throws Exception {
		saveBudget(MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":100000}]}");
		insertExpense(transport, 60_000, "2026-11-10T12:00:00Z");

		read(MONTH)
				.andExpect(jsonPath("$.budget.spent").value(60000))
				.andExpect(jsonPath("$.budget.remaining").value(240000))
				.andExpect(jsonPath("$.budget.lines[0].spent").value(0));
	}

	@Test
	void aBudgetSmallerThanWhatWasSpentIsExceeded() throws Exception {
		saveBudget(MONTH, "{\"amount\":50000}");
		insertExpense(food, 80_000, "2026-11-10T12:00:00Z");

		read(MONTH)
				.andExpect(jsonPath("$.budget.spent").value(80000))
				.andExpect(jsonPath("$.budget.remaining").value(-30000))
				.andExpect(jsonPath("$.budget.exceeded").value(true));
	}

	@Test
	void aFutureMonthWithABudgetShowsItWithSpentZeroAndEmptyTotals() throws Exception {
		saveBudget("2030-01", "{\"amount\":400000}");

		read("2030-01")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.totalExpenses").value(0))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.expensesByCategory.length()").value(0))
				.andExpect(jsonPath("$.budget.amount").value(400000))
				.andExpect(jsonPath("$.budget.spent").value(0))
				.andExpect(jsonPath("$.budget.remaining").value(400000))
				.andExpect(jsonPath("$.budget.exceeded").value(false));
	}

	@Test
	void theSummaryBudgetEqualsTheResponseOfGetBudget() throws Exception {
		saveBudget(MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":200000},"
				+ "{\"categoryId\":" + transport + ",\"amount\":100000},{\"categoryId\":" + health
				+ ",\"amount\":50000}]}");
		insertExpense(food, 250_000, "2026-11-10T12:00:00Z");
		insertExpense(transport, 30_000, "2026-11-11T12:00:00Z");
		insertExpense(food, 99_000, "2026-10-31T12:00:00Z");

		String summary = read(MONTH).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String budget = mockMvc.perform(get("/api/v1/presupuestos/" + MONTH).header("Authorization", ana.bearer()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		Object fromSummary = JsonPath.read(summary, "$.budget");
		Object fromBudget = JsonPath.read(budget, "$");
		assertThat(fromSummary).isEqualTo(fromBudget);
	}

	private void saveBudget(String month, String body) throws Exception {
		mockMvc.perform(put("/api/v1/presupuestos/" + month).header("Authorization", ana.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
	}

	private ResultActions read(String month) throws Exception {
		return mockMvc.perform(get("/api/v1/resumenes/" + month).header("Authorization", ana.bearer()));
	}

	private void insertExpense(long categoryId, long amount, String occurredAt) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at) "
				+ "VALUES (?, ?, 'GASTO', ?, ?::timestamptz)", ana.id(), amount, categoryId, occurredAt);
	}
}
