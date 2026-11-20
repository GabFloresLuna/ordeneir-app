package cl.floresluna.ordenier.summary;

import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

/** R1 (parcial), R2, R6, R7: totales y balance del resumen mensual (GET /resumenes/{mes}). */
class SummaryTotalsTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private long food;
	private long salary;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		salary = predefinedId(jdbcTemplate, "Sueldo", "INGRESO");
	}

	@Test
	void monthWithIncomeAndExpensesReturnsTotalsAndBalance() throws Exception {
		insertMovement(ana, "INGRESO", salary, 1_000_000, "2026-11-05T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 100_000, "2026-11-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 200_000, "2026-11-11T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value(MONTH))
				.andExpect(jsonPath("$.totalIncome").value(1000000))
				.andExpect(jsonPath("$.totalExpenses").value(300000))
				.andExpect(jsonPath("$.balance").value(700000))
				.andExpect(jsonPath("$.expensesByCategory.length()").value(0))
				.andExpect(jsonPath("$.incomeByCategory.length()").value(0))
				.andExpect(jsonPath("$", hasKey("budget")))
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	@Test
	void negativeBalanceIsValid() throws Exception {
		insertMovement(ana, "INGRESO", salary, 100_000, "2026-11-05T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 250_000, "2026-11-10T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(100000))
				.andExpect(jsonPath("$.totalExpenses").value(250000))
				.andExpect(jsonPath("$.balance").value(-150000));
	}

	@Test
	void onlyIncomeLeavesExpensesAtZero() throws Exception {
		insertMovement(ana, "INGRESO", salary, 400_000, "2026-11-05T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.totalIncome").value(400000))
				.andExpect(jsonPath("$.totalExpenses").value(0))
				.andExpect(jsonPath("$.balance").value(400000));
	}

	@Test
	void onlyExpensesLeavesIncomeAtZero() throws Exception {
		insertMovement(ana, "GASTO", food, 60_000, "2026-11-05T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.totalExpenses").value(60000))
				.andExpect(jsonPath("$.balance").value(-60000));
	}

	@Test
	void movementsOfOtherMonthsAndOfTheSameMonthInOtherYearsAreNotCounted() throws Exception {
		insertMovement(ana, "GASTO", food, 10_000, "2026-11-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 20_000, "2026-10-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 40_000, "2026-12-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 80_000, "2025-11-10T12:00:00Z", false);
		insertMovement(ana, "INGRESO", salary, 5_000, "2025-11-10T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.totalExpenses").value(10000))
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.balance").value(-10000));
	}

	@Test
	void deletedMovementsAreNotCounted() throws Exception {
		insertMovement(ana, "GASTO", food, 10_000, "2026-11-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 99_000, "2026-11-11T12:00:00Z", true);
		insertMovement(ana, "INGRESO", salary, 77_000, "2026-11-12T12:00:00Z", true);

		read(ana, MONTH)
				.andExpect(jsonPath("$.totalExpenses").value(10000))
				.andExpect(jsonPath("$.totalIncome").value(0));
	}

	// Chile está en UTC-3 en noviembre; el mes se mide en hora de Santiago (R2).
	@Test
	void firstAndLastInstantOfTheMonthInSantiagoTimeBelongToTheMonth() throws Exception {
		insertMovement(ana, "GASTO", food, 1_000, "2026-11-01T00:00:00-03:00", false);
		insertMovement(ana, "GASTO", food, 2_000, "2026-11-30T23:59:59.999-03:00", false);
		insertMovement(ana, "GASTO", food, 40_000, "2026-10-31T23:59:59.999-03:00", false);
		insertMovement(ana, "GASTO", food, 80_000, "2026-12-01T00:00:00-03:00", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.totalExpenses").value(3000));
	}

	@Test
	void monthWithoutMovementsReturns200WithZeros() throws Exception {
		assertEmptyMonth("2026-09");
	}

	@Test
	void futureMonthReturns200WithZeros() throws Exception {
		assertEmptyMonth("2027-06");
	}

	@Test
	void farthestMonthReturns200WithZeros() throws Exception {
		assertEmptyMonth("9999-12");
	}

	@ParameterizedTest
	@ValueSource(strings = {"2026-13", "2026-1", "abc"})
	void malformedMonthReturns400DatosInvalidos(String month) throws Exception {
		read(ana, month)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	private void assertEmptyMonth(String month) throws Exception {
		read(ana, month)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value(month))
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.totalExpenses").value(0))
				.andExpect(jsonPath("$.balance").value(0))
				.andExpect(jsonPath("$.expensesByCategory.length()").value(0))
				.andExpect(jsonPath("$.incomeByCategory.length()").value(0))
				.andExpect(jsonPath("$.budget").value(nullValue()));
	}

	private ResultActions read(TestUser user, String month) throws Exception {
		return mockMvc.perform(get("/api/v1/resumenes/" + month).header("Authorization", user.bearer()));
	}

	private void insertMovement(TestUser user, String type, long categoryId, long amount, String occurredAt,
			boolean deleted) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at, deleted_at) "
				+ "VALUES (?, ?, ?, ?, ?::timestamptz, CASE WHEN ? THEN now() ELSE NULL END)", user.id(), amount,
				type, categoryId, occurredAt, deleted);
	}
}
