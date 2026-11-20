package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.movement.MovementTestSupport.MOVEMENTS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.movement.MovementTestSupport.TestUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

class MovementListTest extends AbstractIntegrationTest {

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void listIsOrderedByDateDescendingWithTieBrokenByIdDescending() throws Exception {
		insert(ana, "2026-11-15T10:00:00Z", "nuevo-a");
		insert(ana, "2026-11-10T10:00:00Z", "viejo");
		insert(ana, "2026-11-15T10:00:00Z", "nuevo-b");

		list(ana, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("nuevo-b", "nuevo-a", "viejo")));
	}

	@Test
	void listItemsHaveTheMovementShapeWithoutOwner() throws Exception {
		long id = insert(ana, "2026-11-19T18:30:00Z", "Almuerzo");

		list(ana, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(id))
				.andExpect(jsonPath("$.content[0].amount").value(1000))
				.andExpect(jsonPath("$.content[0].type").value("GASTO"))
				.andExpect(jsonPath("$.content[0].occurredAt").value("2026-11-19T18:30:00Z"))
				.andExpect(jsonPath("$.content[0].description").value("Almuerzo"))
				.andExpect(jsonPath("$.content[0].userId").doesNotExist());
	}

	@Test
	void defaultsAreFirstPageOfTwentyWithTotals() throws Exception {
		insertMany(ana, 25);

		list(ana, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(25))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(20));
	}

	@Test
	void secondPageHoldsTheRemainingElements() throws Exception {
		insertMany(ana, 25);

		list(ana, "?page=1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalElements").value(25))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(5));
	}

	@Test
	void sizeOneHundredIsValidAndOneHundredOneIsRejected() throws Exception {
		insertMany(ana, 3);

		list(ana, "?size=100")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100))
				.andExpect(jsonPath("$.totalPages").value(1));
		list(ana, "?size=101")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "?page=-1", "?size=0", "?size=-5", "?page=abc", "?size=abc", "?size=101" })
	void invalidPageOrSizeReturns400DatosInvalidos(String query) throws Exception {
		list(ana, query)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-13", "2026-00", "2026-1", "202611", "noviembre", "2026-11-01", "" })
	void malformedMonthReturns400DatosInvalidos(String mes) throws Exception {
		list(ana, "?mes=" + mes)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void monthFilterUsesSantiagoMonthBoundaries() throws Exception {
		insert(ana, "2026-09-30T23:59:59-03:00", "sep-fin");
		insert(ana, "2026-10-01T00:00:00-03:00", "oct-inicio");
		insert(ana, "2026-11-01T02:30:00Z", "oct-utc");
		insert(ana, "2026-10-31T23:59:59-03:00", "oct-limite");
		insert(ana, "2026-11-01T00:00:00-03:00", "nov-inicio");

		list(ana, "?mes=2026-10")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("oct-limite", "oct-utc", "oct-inicio")))
				.andExpect(jsonPath("$.totalElements").value(3));
		list(ana, "?mes=2026-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("nov-inicio")));
		list(ana, "?mes=2026-09")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("sep-fin")));
	}

	@Test
	void monthFilterFollowsSantiagoWinterOffsetAndNotAFixedOne() throws Exception {
		// En junio Santiago está en UTC-4: el 1 de junio a las 00:00 locales son las 04:00Z.
		insert(ana, "2026-06-01T03:59:59Z", "may-fin");
		insert(ana, "2026-06-01T04:00:00Z", "jun-inicio");

		list(ana, "?mes=2026-06")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("jun-inicio")));
		list(ana, "?mes=2026-05")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("may-fin")));
	}

	@Test
	void monthFilterIsPaginatedWithItsOwnTotals() throws Exception {
		insert(ana, "2026-10-05T12:00:00Z", "oct-1");
		insert(ana, "2026-10-06T12:00:00Z", "oct-2");
		insert(ana, "2026-10-07T12:00:00Z", "oct-3");
		insert(ana, "2026-11-07T12:00:00Z", "nov-1");

		list(ana, "?mes=2026-10&size=1&page=1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("oct-2")))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(1))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void userWithoutMovementsGets200WithEmptyList() throws Exception {
		list(ana, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(empty()))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void monthWithoutMovementsGets200WithEmptyList() throws Exception {
		insert(ana, "2026-10-05T12:00:00Z", "oct-1");

		list(ana, "?mes=2026-12")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(empty()))
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void pageOutOfRangeGets200WithEmptyList() throws Exception {
		insertMany(ana, 3);

		list(ana, "?page=5")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").value(empty()))
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void deletedMovementsAreNotListedNorCounted() throws Exception {
		insert(ana, "2026-11-10T10:00:00Z", "visible");
		long deletedId = insert(ana, "2026-11-11T10:00:00Z", "eliminado");
		jdbcTemplate.update("UPDATE movements SET deleted_at = now() WHERE id = ?", deletedId);

		list(ana, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].description").value(contains("visible")))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void listOnlyHoldsTheCallersMovements() throws Exception {
		insert(ana, "2026-11-10T10:00:00Z", "de-ana");
		insert(bea, "2026-11-11T10:00:00Z", "de-bea");

		list(ana, "")
				.andExpect(jsonPath("$.content[*].description").value(contains("de-ana")))
				.andExpect(jsonPath("$.totalElements").value(1));
		list(bea, "?mes=2026-11")
				.andExpect(jsonPath("$.content[*].description").value(contains("de-bea")))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void listingWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get(MOVEMENTS))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private ResultActions list(TestUser user, String query) throws Exception {
		return mockMvc.perform(get(MOVEMENTS + query).header("Authorization", user.bearer()));
	}

	private long insert(TestUser user, String occurredAt, String description) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO movements (user_id, amount, type, occurred_at, description) "
						+ "VALUES (?, 1000, 'GASTO', CAST(? AS timestamptz), ?) RETURNING id",
				Long.class, user.id(), occurredAt, description);
	}

	private void insertMany(TestUser user, int count) {
		Instant base = Instant.parse("2026-11-01T12:00:00Z");
		for (int i = 0; i < count; i++) {
			insert(user, base.plus(i, ChronoUnit.MINUTES).toString(), "mov-" + i);
		}
	}
}
