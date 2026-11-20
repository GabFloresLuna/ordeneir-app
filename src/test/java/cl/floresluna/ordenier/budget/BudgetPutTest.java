package cl.floresluna.ordenier.budget;

import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.markDeleted;
import static cl.floresluna.ordenier.category.CategoryTestSupport.predefinedId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** R17, R18, R19, R20, R24: guardar el presupuesto mensual (PUT /presupuestos/{mes}). */
class BudgetPutTest extends AbstractIntegrationTest {

	private static final String MONTH = "2026-11";

	private TestUser ana;
	private TestUser bea;
	private long food;
	private long transport;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
		food = predefinedId(jdbcTemplate, "Alimentación", "GASTO");
		transport = predefinedId(jdbcTemplate, "Transporte", "GASTO");
	}

	@Test
	void savingABudgetWithoutLinesReturns200WithTheCompleteBudget() throws Exception {
		send(ana, MONTH, "{\"amount\":500000}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value(MONTH))
				.andExpect(jsonPath("$.amount").value(500000))
				.andExpect(jsonPath("$.spent").value(0))
				.andExpect(jsonPath("$.remaining").value(500000))
				.andExpect(jsonPath("$.exceeded").value(false))
				.andExpect(jsonPath("$.lines").isArray())
				.andExpect(jsonPath("$.lines.length()").value(0));

		assertThat(budgetCount(ana)).isEqualTo(1);
	}

	@Test
	void savingABudgetWithLinesReturns200WithLinesOrderedByCategoryName() throws Exception {
		send(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + transport
				+ ",\"amount\":100000},{\"categoryId\":" + food + ",\"amount\":200000}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(500000))
				.andExpect(jsonPath("$.lines.length()").value(2))
				.andExpect(jsonPath("$.lines[0].categoryId").value(food))
				.andExpect(jsonPath("$.lines[0].amount").value(200000))
				.andExpect(jsonPath("$.lines[0].spent").value(0))
				.andExpect(jsonPath("$.lines[0].remaining").value(200000))
				.andExpect(jsonPath("$.lines[0].exceeded").value(false))
				.andExpect(jsonPath("$.lines[1].categoryId").value(transport))
				.andExpect(jsonPath("$.lines[1].amount").value(100000));

		assertThat(lineCount(ana)).isEqualTo(2);
	}

	@Test
	void savingAgainReplacesThePreviousBudgetAndItsLines() throws Exception {
		send(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":200000},"
				+ "{\"categoryId\":" + transport + ",\"amount\":100000}]}").andExpect(status().isOk());

		send(ana, MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(300000))
				.andExpect(jsonPath("$.lines.length()").value(1))
				.andExpect(jsonPath("$.lines[0].categoryId").value(food))
				.andExpect(jsonPath("$.lines[0].amount").value(50000));

		assertThat(budgetCount(ana)).isEqualTo(1);
		assertThat(lineCount(ana)).isEqualTo(1);

		send(ana, MONTH, "{\"amount\":400000}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines.length()").value(0));
		assertThat(budgetCount(ana)).isEqualTo(1);
		assertThat(lineCount(ana)).isZero();
	}

	@Test
	void eachMonthHasItsOwnBudget() throws Exception {
		send(ana, "2026-10", "{\"amount\":100000}").andExpect(status().isOk());
		send(ana, "2026-11", "{\"amount\":200000}").andExpect(status().isOk());

		assertThat(budgetCount(ana)).isEqualTo(2);
	}

	@Test
	void lineSumEqualToTheGeneralAmountIsValidAndGreaterReturns400() throws Exception {
		send(ana, MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":200000},"
				+ "{\"categoryId\":" + transport + ",\"amount\":100000}]}").andExpect(status().isOk());

		send(ana, MONTH, "{\"amount\":300000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":200000},"
				+ "{\"categoryId\":" + transport + ",\"amount\":100001}]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("SUMA_CATEGORIAS_EXCEDE_GENERAL"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());

		assertThat(lineCount(ana)).isEqualTo(2);
		assertThat(generalAmount(ana)).isEqualTo(300000L);
	}

	@Test
	void sumGreaterThanTheGeneralAmountSavesNothingWhenThereWasNoBudget() throws Exception {
		send(ana, MONTH, "{\"amount\":100,\"lines\":[{\"categoryId\":" + food + ",\"amount\":101}]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("SUMA_CATEGORIAS_EXCEDE_GENERAL"));

		assertThat(budgetCount(ana)).isZero();
	}

	@Test
	void invalidAmountsReturn400DatosInvalidosAndSaveNothing() throws Exception {
		String line = "\"lines\":[{\"categoryId\":" + food + ",\"amount\":";
		List<String> bodies = List.of(
				"{}",
				"{\"amount\":null}",
				"{\"amount\":0}",
				"{\"amount\":-5}",
				"{\"amount\":1.5}",
				"{\"amount\":\"500000\"}",
				"{\"amount\":1000000000001}",
				"{\"amount\":99999999999999999999}",
				"{\"amount\":500000," + line + "0}]}",
				"{\"amount\":500000," + line + "-1}]}",
				"{\"amount\":500000," + line + "10.5}]}",
				"{\"amount\":500000," + line + "\"100\"}]}",
				"{\"amount\":500000," + line + "null}]}",
				"{\"amount\":500000,\"lines\":[{\"categoryId\":" + food + "}]}");

		for (String body : bodies) {
			assertInvalidData(ana, MONTH, body);
		}
		assertThat(budgetCount(ana)).isZero();
	}

	@Test
	void amountBoundariesOneAndOneTrillionAreAccepted() throws Exception {
		send(ana, MONTH, "{\"amount\":1}").andExpect(status().isOk());
		send(ana, MONTH, "{\"amount\":1000000000000}").andExpect(status().isOk());
	}

	@Test
	void malformedMonthReturns400DatosInvalidos() throws Exception {
		for (String month : List.of("2026-13", "2026-00", "2026-1", "26-11", "noviembre", "2026-11-01")) {
			assertInvalidData(ana, month, "{\"amount\":500000}");
		}
		assertThat(budgetCount(ana)).isZero();
	}

	@Test
	void repeatedCategoryReturns400DatosInvalidos() throws Exception {
		assertInvalidData(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + food
				+ ",\"amount\":1000},{\"categoryId\":" + food + ",\"amount\":2000}]}");
		assertThat(budgetCount(ana)).isZero();
	}

	@Test
	void moreThanOneHundredLinesReturns400AndExactlyOneHundredIsAccepted() throws Exception {
		List<Long> ids = new java.util.ArrayList<>();
		for (int i = 0; i < 101; i++) {
			ids.add(insertOwn(jdbcTemplate, ana.id(), "Propia " + i, "GASTO"));
		}

		send(ana, MONTH, bodyWithLines(ids.subList(0, 100))).andExpect(status().isOk());
		assertThat(lineCount(ana)).isEqualTo(100);

		assertInvalidData(ana, MONTH, bodyWithLines(ids));
		assertThat(lineCount(ana)).isEqualTo(100);
	}

	@Test
	void linesThatAreNotAnArrayOrNotObjectsReturn400() throws Exception {
		assertInvalidData(ana, MONTH, "{\"amount\":500000,\"lines\":\"x\"}");
		assertInvalidData(ana, MONTH, "{\"amount\":500000,\"lines\":{}}");
		assertInvalidData(ana, MONTH, "{\"amount\":500000,\"lines\":[5]}");
		assertInvalidData(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":\"" + food + "\",\"amount\":1}]}");
		assertThat(budgetCount(ana)).isZero();
	}

	@Test
	void nullLinesCountAsNoLines() throws Exception {
		send(ana, MONTH, "{\"amount\":500000,\"lines\":null}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lines.length()").value(0));
	}

	@Test
	void incomeForeignDeletedAndMissingCategoriesReturn400CategoriaInvalidaAndSaveNothing() throws Exception {
		long income = predefinedId(jdbcTemplate, "Sueldo", "INGRESO");
		long foreign = insertOwn(jdbcTemplate, bea.id(), "De Bea", "GASTO");
		long deleted = insertOwn(jdbcTemplate, ana.id(), "Vieja", "GASTO");
		markDeleted(jdbcTemplate, deleted);

		String first = null;
		for (long invalid : new long[] { income, foreign, deleted, 999_999L }) {
			String body = send(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + food
					+ ",\"amount\":1000},{\"categoryId\":" + invalid + ",\"amount\":1000}]}")
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("CATEGORIA_INVALIDA"))
					.andReturn().getResponse().getContentAsString();
			if (first == null) {
				first = body;
			}
			assertThat(body).isEqualTo(first);
		}
		assertThat(budgetCount(ana)).isZero();
		assertThat(lineCount(ana)).isZero();
	}

	@Test
	void invalidCategoryDoesNotTouchAnExistingBudget() throws Exception {
		send(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":1000}]}")
				.andExpect(status().isOk());
		long income = predefinedId(jdbcTemplate, "Sueldo", "INGRESO");

		send(ana, MONTH, "{\"amount\":999,\"lines\":[{\"categoryId\":" + income + ",\"amount\":1}]}")
				.andExpect(status().isBadRequest());

		assertThat(generalAmount(ana)).isEqualTo(500000L);
		assertThat(lineCount(ana)).isEqualTo(1);
	}

	@Test
	void ownAndPredefinedExpenseCategoriesAreAccepted() throws Exception {
		long own = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		send(ana, MONTH, "{\"amount\":500000,\"lines\":[{\"categoryId\":" + own + ",\"amount\":1000},"
				+ "{\"categoryId\":" + food + ",\"amount\":1000}]}").andExpect(status().isOk());
	}

	@Test
	void aBudgetBelowWhatIsAlreadySpentIsAcceptedAndShownAsExceeded() throws Exception {
		insertExpense(ana, food, 80_000, "2026-11-10T12:00:00Z");
		insertExpense(ana, transport, 30_000, "2026-11-11T12:00:00Z");

		send(ana, MONTH, "{\"amount\":100000,\"lines\":[{\"categoryId\":" + food + ",\"amount\":50000}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spent").value(110000))
				.andExpect(jsonPath("$.remaining").value(-10000))
				.andExpect(jsonPath("$.exceeded").value(true))
				.andExpect(jsonPath("$.lines[0].spent").value(80000))
				.andExpect(jsonPath("$.lines[0].remaining").value(-30000))
				.andExpect(jsonPath("$.lines[0].exceeded").value(true));
	}

	@Test
	void ownerComesFromTheTokenAndIdOrUserIdInTheBodyAreIgnored() throws Exception {
		send(ana, MONTH, "{\"id\":99,\"userId\":" + bea.id() + ",\"amount\":500000}").andExpect(status().isOk());

		assertThat(budgetCount(ana)).isEqualTo(1);
		assertThat(budgetCount(bea)).isZero();
	}

	@Test
	void saveWithoutTokenReturns401() throws Exception {
		mockMvc.perform(put("/api/v1/presupuestos/" + MONTH).contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":500000}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	@Test
	void expenseOfAnotherMonthDoesNotCount() throws Exception {
		insertExpense(ana, food, 80_000, "2026-10-31T12:00:00Z");

		send(ana, MONTH, "{\"amount\":100000}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.spent").value(0));
	}

	private String bodyWithLines(List<Long> ids) {
		StringBuilder lines = new StringBuilder();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) {
				lines.append(',');
			}
			lines.append("{\"categoryId\":").append(ids.get(i)).append(",\"amount\":1}");
		}
		return "{\"amount\":1000,\"lines\":[" + lines + "]}";
	}

	private void insertExpense(TestUser user, long categoryId, long amount, String occurredAt) {
		jdbcTemplate.update("INSERT INTO movements (user_id, amount, type, category_id, occurred_at) "
				+ "VALUES (?, ?, 'GASTO', ?, ?::timestamptz)", user.id(), amount, categoryId, occurredAt);
	}

	private void assertInvalidData(TestUser user, String month, String body) throws Exception {
		send(user, month, body)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	private ResultActions send(TestUser user, String month, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/presupuestos/" + month).header("Authorization", user.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private int budgetCount(TestUser user) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM budgets WHERE user_id = ?", Integer.class,
				user.id());
	}

	private int lineCount(TestUser user) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM budget_lines l JOIN budgets b ON b.id = l.budget_id "
				+ "WHERE b.user_id = ?", Integer.class, user.id());
	}

	private long generalAmount(TestUser user) {
		return jdbcTemplate.queryForObject("SELECT amount FROM budgets WHERE user_id = ?", Long.class, user.id());
	}
}
