package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.movement.MovementTestSupport.MOVEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.movement.MovementTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MovementDeleteTest extends AbstractIntegrationTest {

	private static final String DATE = "2026-11-19T15:30:00-03:00";

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void deletingOwnMovementReturns204WithoutBodyAndKeepsRowWithDeletedAt() throws Exception {
		long id = createFor(ana);

		String content = remove(ana, id)
				.andExpect(status().isNoContent())
				.andReturn().getResponse().getContentAsString();

		assertThat(content).isEmpty();
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT user_id, amount, deleted_at FROM movements WHERE id = ?", id);
		assertThat(row.get("deleted_at")).isNotNull();
		assertThat(row.get("user_id")).isEqualTo(ana.id());
		assertThat(row.get("amount")).isEqualTo(15000L);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Long.class)).isEqualTo(1L);
	}

	@Test
	void deletedMovementDisappearsFromListAndCannotBeViewedEditedOrDeletedAgain() throws Exception {
		long deletedId = createFor(ana);
		long keptId = createFor(ana);
		remove(ana, deletedId).andExpect(status().isNoContent());

		mockMvc.perform(get(MOVEMENTS).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(keptId));

		String view = notFoundBody(mockMvc.perform(get(MOVEMENTS + "/" + deletedId)
				.header("Authorization", ana.bearer())));
		notFoundBody(mockMvc.perform(put(MOVEMENTS + "/" + deletedId).header("Authorization", ana.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amount\":100,\"type\":\"GASTO\",\"occurredAt\":\"" + DATE + "\"}")));
		String again = notFoundBody(remove(ana, deletedId));

		assertThat(again).isEqualTo(view);
	}

	@Test
	void deletingTwiceKeepsTheOriginalDeletionTime() throws Exception {
		long id = createFor(ana);
		remove(ana, id).andExpect(status().isNoContent());
		Object first = jdbcTemplate.queryForObject("SELECT deleted_at FROM movements WHERE id = ?",
				java.sql.Timestamp.class, id);

		remove(ana, id).andExpect(status().isNotFound());

		Object second = jdbcTemplate.queryForObject("SELECT deleted_at FROM movements WHERE id = ?",
				java.sql.Timestamp.class, id);
		assertThat(second).isEqualTo(first);
	}

	@Test
	void deletingNonexistentOrOtherUsersMovementReturnsIdentical404AndLeavesOthersIntact() throws Exception {
		long othersId = createFor(bea);

		String nonexistent = notFoundBody(remove(ana, 999999L));
		String other = notFoundBody(remove(ana, othersId));

		assertThat(other).isEqualTo(nonexistent);
		assertThat((String) JsonPath.read(nonexistent, "$.codigo")).isEqualTo("MOVIMIENTO_NO_ENCONTRADO");
		assertThat(jdbcTemplate.queryForObject("SELECT deleted_at FROM movements WHERE id = ?",
				java.sql.Timestamp.class, othersId)).isNull();
	}

	@Test
	void nonNumericIdReturns400DatosInvalidos() throws Exception {
		remove(ana, "abc")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void deletingWithoutTokenReturns401() throws Exception {
		mockMvc.perform(delete(MOVEMENTS + "/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private long createFor(TestUser user) throws Exception {
		String response = mockMvc.perform(post(MOVEMENTS).header("Authorization", user.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":15000,\"type\":\"GASTO\",\"occurredAt\":\"" + DATE
								+ "\",\"description\":\"Almuerzo\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private ResultActions remove(TestUser user, Object id) throws Exception {
		return mockMvc.perform(delete(MOVEMENTS + "/" + id).header("Authorization", user.bearer()));
	}

	private String notFoundBody(ResultActions result) throws Exception {
		return result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("MOVIMIENTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
	}
}
