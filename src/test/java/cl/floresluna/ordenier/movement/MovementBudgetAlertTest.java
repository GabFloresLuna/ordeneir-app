package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static cl.floresluna.ordenier.movement.MovementTestSupport.MOVEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.movement.MovementTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R25, R26, R27: aviso de exceso de presupuesto en crear y editar movimientos (plan 003 D12). */
class MovementBudgetAlertTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";
	private static final String DATE = "2026-11-10T12:00:00Z";

	private TestUser ana;
	private long food;
	private long transport;
	private long salary;

	@BeforeEach
	void setUp() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		transport = predefinedId(jdbcTemplate, "Transporte", "GASTO");
		salary = predefinedId(jdbcTemplate, "Sueldo", "INGRESO");
	}

	@Test
	void createExpenseWithinBothLimitsReportsNothingExceeded() throws Exception {
		saveBudget("{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");

		create(body(food, "GASTO", 30_000, DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget.generalExceeded").value(false))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(false));
	}

	@Test
	void createExpenseCountsTheNewExpenseWhenCheckingTheCategoryLimit() throws Exception {
		saveBudget("{\"amount\":500000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");
		create(body(food, "GASTO", 40_000, DATE)).andExpect(status().isCreated());

		create(body(food, "GASTO", 20_000, DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget.generalExceeded").value(false))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(true));
	}

	@Test
	void createExpenseCountsTheNewExpenseWhenCheckingTheGeneralLimit() throws Exception {
		saveBudget("{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":90000}]}");
		create(body(transport, "GASTO", 80_000, DATE)).andExpect(status().isCreated());

		create(body(food, "GASTO", 30_000, DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget.generalExceeded").value(true))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(false));
	}

	@Test
	void spendingExactlyTheLimitIsNotExceeded() throws Exception {
		saveBudget("{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":100000}]}");

		create(body(food, "GASTO", 100_000, DATE))
				.andExpect(jsonPath("$.budget.generalExceeded").value(false))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(false));
	}

	@Test
	void categoryExceededIsNullWhenTheCategoryHasNoBudgetLine() throws Exception {
		saveBudget("{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");

		create(body(transport, "GASTO", 10_000, DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget.generalExceeded").value(false))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(nullValue()));
	}

	@Test
	void createIncomeReturnsNullBudgetEvenWithABudget() throws Exception {
		saveBudget("{\"amount\":1000}");

		create(body(salary, "INGRESO", 900_000, DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void createExpenseInAMonthWithoutBudgetReturnsNullBudget() throws Exception {
		saveBudget("{\"amount\":1000}");

		create(body(food, "GASTO", 50_000, "2026-10-10T12:00:00Z"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void createExpenseAfterBudgetIsDeletedReturnsNullBudget() throws Exception {
		saveBudget("{\"amount\":1000}");
		jdbcTemplate.update("DELETE FROM budgets");

		create(body(food, "GASTO", 50_000, DATE))
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void anotherUsersBudgetIsNotUsed() throws Exception {
		TestUser bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");
		mockMvc.perform(put("/api/v1/presupuestos/" + MONTH)
						.header("Authorization", bea.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":1000}"))
				.andExpect(status().isOk());

		create(body(food, "GASTO", 50_000, DATE))
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void expenseThatExceedsTheBudgetIsStillCreated() throws Exception {
		saveBudget("{\"amount\":1000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":1000}]}");

		create(body(food, "GASTO", 999_999, DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.budget.generalExceeded").value(true))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(true));

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Long.class)).isEqualTo(1L);
	}

	@Test
	void updateExpenseReportsExceededWithTheEditedAmount() throws Exception {
		saveBudget("{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");
		long id = createAndGetId(body(food, "GASTO", 10_000, DATE));

		update(id, body(food, "GASTO", 60_000, DATE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget.generalExceeded").value(false))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(true));
	}

	@Test
	void updateDoesNotCountTheOldAmountTwice() throws Exception {
		saveBudget("{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");
		long id = createAndGetId(body(food, "GASTO", 45_000, DATE));

		update(id, body(food, "GASTO", 46_000, DATE))
				.andExpect(jsonPath("$.budget.categoryExceeded").value(false));
	}

	@Test
	void updateExpenseMovedToAMonthWithoutBudgetReturnsNullBudget() throws Exception {
		saveBudget("{\"amount\":1000}");
		long id = createAndGetId(body(food, "GASTO", 500, DATE));

		update(id, body(food, "GASTO", 500, "2026-10-10T12:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void updateIntoIncomeReturnsNullBudget() throws Exception {
		saveBudget("{\"amount\":1000}");
		long id = createAndGetId(body(food, "GASTO", 500, DATE));

		update(id, body(salary, "INGRESO", 500, DATE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void getAndListReturnNullBudget() throws Exception {
		saveBudget("{\"amount\":1000}");
		long id = createAndGetId(body(food, "GASTO", 5_000, DATE));

		mockMvc.perform(get(MOVEMENTS + "/" + id).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.budget").value(nullValue()));
		mockMvc.perform(get(MOVEMENTS).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].budget").value(nullValue()));
	}

	private void saveBudget(String json) throws Exception {
		mockMvc.perform(put("/api/v1/presupuestos/" + MONTH)
						.header("Authorization", ana.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content(json))
				.andExpect(status().isOk());
	}

	private ResultActions create(String json) throws Exception {
		return mockMvc.perform(post(MOVEMENTS)
				.header("Authorization", ana.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private ResultActions update(long id, String json) throws Exception {
		return mockMvc.perform(put(MOVEMENTS + "/" + id)
				.header("Authorization", ana.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private long createAndGetId(String json) throws Exception {
		String response = create(json).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private static String body(long categoryId, String type, long amount, String occurredAt) {
		return "{\"categoryId\":" + categoryId + ",\"amount\":" + amount + ",\"type\":\"" + type
				+ "\",\"occurredAt\":\"" + occurredAt + "\"}";
	}
}
