package cl.floresluna.ordenier.summary;

import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** R1 (parcial), R3: desglose de gastos e ingresos por categoría en GET /resumenes/{mes}. */
class SummaryCategoriesTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private long food;
	private long transport;
	private long salary;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		transport = predefinedId(jdbcTemplate, "Transporte", "GASTO");
		salary = predefinedId(jdbcTemplate, "Sueldo", "INGRESO");
	}

	@Test
	void eachListHoldsOnlyCategoriesWithMovementsOfItsTypeWithIdNameAndTotal() throws Exception {
		insertMovement("GASTO", food, 100_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", food, 50_000, "2026-11-11T12:00:00Z", false);
		insertMovement("INGRESO", salary, 900_000, "2026-11-05T12:00:00Z", false);

		read(MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expensesByCategory.length()").value(1))
				.andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(food))
				.andExpect(jsonPath("$.expensesByCategory[0].name").value("Alimentación"))
				.andExpect(jsonPath("$.expensesByCategory[0].total").value(150000))
				.andExpect(jsonPath("$.incomeByCategory.length()").value(1))
				.andExpect(jsonPath("$.incomeByCategory[0].categoryId").value(salary))
				.andExpect(jsonPath("$.incomeByCategory[0].name").value("Sueldo"))
				.andExpect(jsonPath("$.incomeByCategory[0].total").value(900000));
	}

	@Test
	void predefinedAndOwnCategoriesAreMixed() throws Exception {
		long own = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		insertMovement("GASTO", food, 30_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", own, 70_000, "2026-11-11T12:00:00Z", false);

		read(MONTH)
				.andExpect(jsonPath("$.expensesByCategory.length()").value(2))
				.andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(own))
				.andExpect(jsonPath("$.expensesByCategory[0].name").value("Mascotas"))
				.andExpect(jsonPath("$.expensesByCategory[1].categoryId").value(food));
	}

	@Test
	void listsAreOrderedByTotalDescending() throws Exception {
		insertMovement("GASTO", food, 10_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", transport, 90_000, "2026-11-11T12:00:00Z", false);

		read(MONTH)
				.andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(transport))
				.andExpect(jsonPath("$.expensesByCategory[1].categoryId").value(food));
	}

	@Test
	void categoriesWithTheSameTotalAreTiedByNameIgnoringCase() throws Exception {
		long zeta = insertOwn(jdbcTemplate, ana.id(), "zeta", "GASTO");
		long beta = insertOwn(jdbcTemplate, ana.id(), "Beta", "GASTO");
		long alfa = insertOwn(jdbcTemplate, ana.id(), "alfa", "GASTO");
		insertMovement("GASTO", zeta, 5_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", beta, 5_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", alfa, 5_000, "2026-11-10T12:00:00Z", false);

		read(MONTH)
				.andExpect(jsonPath("$.expensesByCategory[0].name").value("alfa"))
				.andExpect(jsonPath("$.expensesByCategory[1].name").value("Beta"))
				.andExpect(jsonPath("$.expensesByCategory[2].name").value("zeta"));
	}

	@Test
	void sumOfEachListEqualsItsTotal() throws Exception {
		insertMovement("GASTO", food, 10_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", transport, 25_000, "2026-11-11T12:00:00Z", false);
		insertMovement("INGRESO", salary, 500_000, "2026-11-05T12:00:00Z", false);

		read(MONTH)
				.andExpect(jsonPath("$.totalExpenses").value(35000))
				.andExpect(jsonPath("$.expensesByCategory[0].total").value(25000))
				.andExpect(jsonPath("$.expensesByCategory[1].total").value(10000))
				.andExpect(jsonPath("$.totalIncome").value(500000))
				.andExpect(jsonPath("$.incomeByCategory[0].total").value(500000))
				.andExpect(jsonPath("$.balance").value(465000));
	}

	@Test
	void deletedMovementsAndOtherMonthsDoNotCount() throws Exception {
		insertMovement("GASTO", food, 10_000, "2026-11-10T12:00:00Z", false);
		insertMovement("GASTO", food, 99_000, "2026-11-11T12:00:00Z", true);
		insertMovement("GASTO", transport, 40_000, "2026-10-10T12:00:00Z", false);
		insertMovement("GASTO", transport, 80_000, "2025-11-10T12:00:00Z", false);

		read(MONTH)
				.andExpect(jsonPath("$.expensesByCategory.length()").value(1))
				.andExpect(jsonPath("$.expensesByCategory[0].categoryId").value(food))
				.andExpect(jsonPath("$.expensesByCategory[0].total").value(10000));
	}

	@Test
	void emptyMonthKeepsEmptyLists() throws Exception {
		read("2026-09")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expensesByCategory.length()").value(0))
				.andExpect(jsonPath("$.incomeByCategory.length()").value(0));
	}

	private ResultActions read(String month) throws Exception {
		return mockMvc.perform(get("/api/v1/resumenes/" + month).header("Authorization", ana.bearer()));
	}

	private void insertMovement(String type, long categoryId, long amount, String occurredAt, boolean deleted) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at, deleted_at) "
				+ "VALUES (?, ?, ?, ?, ?::timestamptz, CASE WHEN ? THEN now() ELSE NULL END)", ana.id(), amount,
				type, categoryId, occurredAt, deleted);
	}
}
