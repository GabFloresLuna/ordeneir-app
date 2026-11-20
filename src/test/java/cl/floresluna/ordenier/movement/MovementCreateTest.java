package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.movement.MovementTestSupport.MOVEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.movement.MovementTestSupport.TestUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MovementCreateTest extends AbstractIntegrationTest {

	private static final String VALID_DATE = "2026-11-19T15:30:00-03:00";

	private TestUser ana;

	@BeforeEach
	void registerAna() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
	}

	@Test
	void validCreateReturns201WithDataAndDateInUtcAndTokenOwner() throws Exception {
		send(body(15000, "GASTO", VALID_DATE, "Almuerzo"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.amount").value(15000))
				.andExpect(jsonPath("$.type").value("GASTO"))
				.andExpect(jsonPath("$.occurredAt").value("2026-11-19T18:30:00Z"))
				.andExpect(jsonPath("$.description").value("Almuerzo"))
				.andExpect(jsonPath("$.userId").doesNotExist());

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT user_id, amount, type FROM movements");
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("amount")).isEqualTo(15000L);
		assertThat(row.get("type")).isEqualTo("GASTO");
	}

	@Test
	void createWithoutDescriptionReturns201WithNullDescription() throws Exception {
		send("{\"amount\":100,\"type\":\"INGRESO\",\"occurredAt\":\"" + VALID_DATE + "\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void descriptionIsTrimmedAndBlankDescriptionIsStoredAsNull() throws Exception {
		send(body(100, "GASTO", VALID_DATE, "  Café  "))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").value("Café"));
		send(body(100, "GASTO", VALID_DATE, "   "))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void amountLimitsOneAndOneTrillionAreAccepted() throws Exception {
		send(body(1, "GASTO", VALID_DATE, null)).andExpect(status().isCreated());
		send(body(1_000_000_000_000L, "INGRESO", VALID_DATE, null)).andExpect(status().isCreated());
	}

	@Test
	void invalidAmountsReturn400AndCreateNothing() throws Exception {
		List<String> amounts = List.of("0", "-1", "1000000000001", "10.5", "10.0", "\"100\"", "true", "null",
				"99999999999999999999999");
		for (String amount : amounts) {
			assertInvalid(send("{\"amount\":" + amount + ",\"type\":\"GASTO\",\"occurredAt\":\""
					+ VALID_DATE + "\"}"));
		}
		assertInvalid(send("{\"type\":\"GASTO\",\"occurredAt\":\"" + VALID_DATE + "\"}"));
		assertNothingCreated();
	}

	@Test
	void invalidTypesReturn400AndCreateNothing() throws Exception {
		for (String type : List.of("\"ingreso\"", "\"OTRO\"", "\"\"", "1", "null")) {
			assertInvalid(send("{\"amount\":100,\"type\":" + type + ",\"occurredAt\":\"" + VALID_DATE + "\"}"));
		}
		assertInvalid(send("{\"amount\":100,\"occurredAt\":\"" + VALID_DATE + "\"}"));
		assertNothingCreated();
	}

	@Test
	void invalidDatesReturn400AndCreateNothing() throws Exception {
		for (String date : List.of("\"2026-11-19T15:30:00\"", "\"2026-11-19\"", "1763580600", "\"mañana\"",
				"\"\"", "null")) {
			assertInvalid(send("{\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":" + date + "}"));
		}
		assertInvalid(send("{\"amount\":100,\"type\":\"GASTO\"}"));
		assertNothingCreated();
	}

	@Test
	void descriptionOf200CharactersIsAcceptedAndOf201Returns400() throws Exception {
		send(body(100, "GASTO", VALID_DATE, "a".repeat(200))).andExpect(status().isCreated());
		assertInvalid(send(body(100, "GASTO", VALID_DATE, "a".repeat(201))));
		Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void descriptionThatIsNotTextReturns400() throws Exception {
		assertInvalid(send("{\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":\"" + VALID_DATE
				+ "\",\"description\":5}"));
		assertNothingCreated();
	}

	@Test
	void dateOneMinuteInTheFutureIsAcceptedAndTenMinutesReturns400() throws Exception {
		send(body(100, "GASTO", inFuture(1), null)).andExpect(status().isCreated());
		assertInvalid(send(body(100, "GASTO", inFuture(10), null)));
		Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void bodyWithUserIdAndIdDoesNotChangeOwnerNorId() throws Exception {
		TestUser bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");

		send("{\"id\":999,\"userId\":" + bea.id() + ",\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":\""
				+ VALID_DATE + "\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(999)));

		Map<String, Object> row = jdbcTemplate.queryForMap("SELECT id, user_id FROM movements");
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("id")).isNotEqualTo(999L);
	}

	@Test
	void malformedJsonReturns400DatosInvalidos() throws Exception {
		assertInvalid(send("{\"amount\":100,"));
		assertNothingCreated();
	}

	@Test
	void createWithoutTokenReturns401() throws Exception {
		mockMvc.perform(post(MOVEMENTS).contentType(MediaType.APPLICATION_JSON)
						.content(body(100, "GASTO", VALID_DATE, null)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private static String inFuture(long minutes) {
		return OffsetDateTime.ofInstant(Instant.now().plus(minutes, ChronoUnit.MINUTES), ZoneOffset.UTC)
				.toString();
	}

	private static String body(long amount, String type, String date, String description) {
		String desc = description == null ? "" : ",\"description\":\"" + description + "\"";
		return "{\"amount\":" + amount + ",\"type\":\"" + type + "\",\"occurredAt\":\"" + date + "\"" + desc + "}";
	}

	private ResultActions send(String json) throws Exception {
		return mockMvc.perform(post(MOVEMENTS).header("Authorization", ana.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private void assertInvalid(ResultActions result) throws Exception {
		result.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	private void assertNothingCreated() {
		Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Integer.class);
		assertThat(count).isZero();
	}
}
