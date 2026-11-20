package cl.floresluna.ordenier.budget;

import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R21, R22, R23, R24: ver y eliminar el presupuesto mensual (GET y DELETE /presupuestos/{mes}). */
class BudgetReadDeleteTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private TestUser bea;
	private long food;
	private long transport;
	private long salary;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		transport = predefinedId(jdbcTemplate, "Transporte", "GASTO");
		salary = predefinedId(jdbcTemplate, "Sueldo", "INGRESO");
	}

	@Test
	void getReturnsTheGeneralAmountAndLinesWithSpentRemainingAndExceeded() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + transport
				+ ",\"amount\":100000},{\"categoryId\":" + food + ",\"amount\":200000}]}");
		insertMovement(ana, "GASTO", food, 80_000, "2026-11-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", transport, 120_000, "2026-11-11T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value(MONTH))
				.andExpect(jsonPath("$.amount").value(500000))
				.andExpect(jsonPath("$.spent").value(200000))
				.andExpect(jsonPath("$.remaining").value(300000))
				.andExpect(jsonPath("$.exceeded").value(false))
				.andExpect(jsonPath("$.lines.length()").value(2))
				.andExpect(jsonPath("$.lines[0].categoryId").value(food))
				.andExpect(jsonPath("$.lines[0].amount").value(200000))
				.andExpect(jsonPath("$.lines[0].spent").value(80000))
				.andExpect(jsonPath("$.lines[0].remaining").value(120000))
				.andExpect(jsonPath("$.lines[0].exceeded").value(false))
				.andExpect(jsonPath("$.lines[1].categoryId").value(transport))
				.andExpect(jsonPath("$.lines[1].amount").value(100000))
				.andExpect(jsonPath("$.lines[1].spent").value(120000))
				.andExpect(jsonPath("$.lines[1].remaining").value(-20000))
				.andExpect(jsonPath("$.lines[1].exceeded").value(true));
	}

	@Test
	void getWithoutLinesAndWithoutMovementsShowsZeroSpent() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":500000}");

		read(ana, MONTH)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spent").value(0))
				.andExpect(jsonPath("$.remaining").value(500000))
				.andExpect(jsonPath("$.exceeded").value(false))
				.andExpect(jsonPath("$.lines.length()").value(0));
	}

	@Test
	void incomeIsNotCounted() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");
		insertMovement(ana, "INGRESO", salary, 900_000, "2026-11-05T12:00:00Z", false);

		read(ana, MONTH).andExpect(jsonPath("$.spent").value(0));
	}

	@Test
	void expensesOfOtherMonthsAreNotCounted() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");
		insertMovement(ana, "GASTO", food, 10_000, "2026-10-15T12:00:00Z", false);
		insertMovement(ana, "GASTO", food, 20_000, "2026-12-15T12:00:00Z", false);

		read(ana, MONTH).andExpect(jsonPath("$.spent").value(0));
	}

	@Test
	void deletedMovementsAreNotCounted() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");
		insertMovement(ana, "GASTO", food, 30_000, "2026-11-10T12:00:00Z", true);
		insertMovement(ana, "GASTO", food, 5_000, "2026-11-10T13:00:00Z", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.spent").value(5000))
				.andExpect(jsonPath("$.lines[0].spent").value(5000));
	}

	@Test
	void theMonthIsMeasuredInSantiagoTime() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":1000000}");
		// En noviembre Santiago está en UTC-3: el mes va de 03:00Z del día 1 a 03:00Z del 1 de diciembre.
		insertMovement(ana, "GASTO", food, 1, "2026-11-01T02:59:59Z", false); // 31 oct, no cuenta
		insertMovement(ana, "GASTO", food, 10, "2026-11-01T03:00:00Z", false); // cuenta
		insertMovement(ana, "GASTO", food, 100, "2026-12-01T02:59:59Z", false); // 30 nov 23:59, cuenta
		insertMovement(ana, "GASTO", food, 1000, "2026-12-01T03:00:00Z", false); // 1 dic, no cuenta

		read(ana, MONTH).andExpect(jsonPath("$.spent").value(110));
	}

	@Test
	void anExpenseWithoutALineCountsForTheGeneralAmountOnly() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");
		insertMovement(ana, "GASTO", food, 20_000, "2026-11-10T12:00:00Z", false);
		insertMovement(ana, "GASTO", transport, 30_000, "2026-11-11T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.spent").value(50000))
				.andExpect(jsonPath("$.remaining").value(50000))
				.andExpect(jsonPath("$.lines.length()").value(1))
				.andExpect(jsonPath("$.lines[0].spent").value(20000));
	}

	@Test
	void aBudgetBelowWhatWasSpentShowsNegativeRemainingAndExceeded() throws Exception {
		insertMovement(ana, "GASTO", food, 80_000, "2026-11-10T12:00:00Z", false);
		saveBudget(ana, MONTH, "{\"amount\":50000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":20000}]}");

		read(ana, MONTH)
				.andExpect(jsonPath("$.spent").value(80000))
				.andExpect(jsonPath("$.remaining").value(-30000))
				.andExpect(jsonPath("$.exceeded").value(true))
				.andExpect(jsonPath("$.lines[0].remaining").value(-60000))
				.andExpect(jsonPath("$.lines[0].exceeded").value(true));
	}

	@Test
	void spentEqualToTheAmountIsNotExceeded() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":50000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");
		insertMovement(ana, "GASTO", food, 50_000, "2026-11-10T12:00:00Z", false);

		read(ana, MONTH)
				.andExpect(jsonPath("$.remaining").value(0))
				.andExpect(jsonPath("$.exceeded").value(false))
				.andExpect(jsonPath("$.lines[0].remaining").value(0))
				.andExpect(jsonPath("$.lines[0].exceeded").value(false));
	}

	@Test
	void expensesOfAnotherUserAreNotCounted() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");
		insertMovement(bea, "GASTO", food, 70_000, "2026-11-10T12:00:00Z", false);

		read(ana, MONTH).andExpect(jsonPath("$.spent").value(0));
	}

	@Test
	void getWithoutBudgetReturns404PresupuestoNoEncontrado() throws Exception {
		read(ana, MONTH)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void getOfAnotherMonthWithoutBudgetReturns404() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");

		read(ana, "2026-10")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"));
	}

	@Test
	void getDoesNotShowTheBudgetOfAnotherUser() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");

		read(bea, MONTH)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"));
	}

	@Test
	void malformedMonthReturns400DatosInvalidosOnGetAndDelete() throws Exception {
		for (String month : new String[] { "2026-13", "2026-00", "2026-1", "26-11", "noviembre",
				"2026-11-01" }) {
			read(ana, month)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
			remove(ana, month)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
		}
	}

	@Test
	void deleteReturns204WithoutBodyAndLeavesTheMonthWithoutBudget() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}");

		remove(ana, MONTH)
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		read(ana, MONTH)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"));
		assertThat(count("budgets", ana)).isZero();
		assertThat(lineCount()).isZero();
	}

	@Test
	void deletingTwiceReturns404TheSecondTime() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");
		remove(ana, MONTH).andExpect(status().isNoContent());

		remove(ana, MONTH)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void deleteWithoutBudgetReturns404() throws Exception {
		remove(ana, MONTH)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"));
	}

	@Test
	void deleteOnlyRemovesThatMonthAndThatUser() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");
		saveBudget(ana, "2026-10", "{\"amount\":200000}");
		saveBudget(bea, MONTH, "{\"amount\":300000}");

		remove(ana, MONTH).andExpect(status().isNoContent());

		read(ana, "2026-10").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(200000));
		read(bea, MONTH).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(300000));
	}

	@Test
	void deleteDoesNotRemoveTheBudgetOfAnotherUser() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");

		remove(bea, MONTH)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRESUPUESTO_NO_ENCONTRADO"));

		assertThat(count("budgets", ana)).isEqualTo(1);
	}

	@Test
	void deleteKeepsTheMovementsAndAllowsSavingANewBudget() throws Exception {
		saveBudget(ana, MONTH, "{\"amount\":100000}");
		insertMovement(ana, "GASTO", food, 10_000, "2026-11-10T12:00:00Z", false);

		remove(ana, MONTH).andExpect(status().isNoContent());

		assertThat(count("movements", ana)).isEqualTo(1);
		saveBudget(ana, MONTH, "{\"amount\":60000}");
		read(ana, MONTH).andExpect(jsonPath("$.amount").value(60000)).andExpect(jsonPath("$.spent").value(10000));
	}

	@Test
	void readAndDeleteWithoutTokenReturn401() throws Exception {
		mockMvc.perform(get("/api/v1/presupuestos/" + MONTH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
		mockMvc.perform(delete("/api/v1/presupuestos/" + MONTH))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private void saveBudget(TestUser user, String month, String body) throws Exception {
		mockMvc.perform(put("/api/v1/presupuestos/" + month).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
	}

	private ResultActions read(TestUser user, String month) throws Exception {
		return mockMvc.perform(get("/api/v1/presupuestos/" + month).header("Authorization", user.bearer()));
	}

	private ResultActions remove(TestUser user, String month) throws Exception {
		return mockMvc.perform(delete("/api/v1/presupuestos/" + month).header("Authorization", user.bearer()));
	}

	private void insertMovement(TestUser user, String type, long categoryId, long amount, String occurredAt,
			boolean deleted) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at, deleted_at) "
				+ "VALUES (?, ?, ?, ?, ?::timestamptz, CASE WHEN ? THEN now() ELSE NULL END)", user.id(), amount,
				type, categoryId, occurredAt, deleted);
	}

	private int count(String table, TestUser user) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class,
				user.id());
	}

	private int lineCount() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM budget_lines", Integer.class);
	}
}
