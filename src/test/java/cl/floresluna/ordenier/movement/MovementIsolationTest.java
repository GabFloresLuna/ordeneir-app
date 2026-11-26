package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.category.CategoryTestSupport.categoryField;
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

/** R19 / constitución art. 6: un usuario no ve, lista, edita ni elimina movimientos de otro. */
class MovementIsolationTest extends AbstractIntegrationTest {

	private static final String DATE = "2026-11-19T15:30:00-03:00";

	private TestUser ana;
	private TestUser bea;
	private long anaId1;
	private long anaId2;
	private long beaId;

	@BeforeEach
	void registerUsersWithMovements() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");
		anaId1 = createFor(ana, 15000, "INGRESO", "Sueldo de Ana");
		anaId2 = createFor(ana, 2500, "GASTO", "Micro de Ana");
		beaId = createFor(bea, 9000, "GASTO", "Cine de Bea");
	}

	@Test
	void otherUserCannotViewMovementAndGetsSame404AsNonexistent() throws Exception {
		String foreign = notFoundBody(mockMvc.perform(get(MOVEMENTS + "/" + anaId1)
				.header("Authorization", bea.bearer())));
		String nonexistent = notFoundBody(mockMvc.perform(get(MOVEMENTS + "/999999")
				.header("Authorization", bea.bearer())));

		assertThat(foreign).isEqualTo(nonexistent);
		assertThat(foreign).doesNotContain("Sueldo de Ana");
	}

	@Test
	void otherUsersListNeverIncludesMyMovements() throws Exception {
		String body = mockMvc.perform(get(MOVEMENTS).header("Authorization", bea.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(beaId))
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain("Sueldo de Ana").doesNotContain("Micro de Ana");
	}

	@Test
	void otherUserCannotEditMovementAndItStaysIntact() throws Exception {
		Map<String, Object> before = rowOf(anaId1);

		notFoundBody(mockMvc.perform(put(MOVEMENTS + "/" + anaId1).header("Authorization", bea.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{" + cat("GASTO") + "\"amount\":1,\"type\":\"GASTO\",\"occurredAt\":\"" + DATE
						+ "\",\"description\":\"Hackeado\"}")));

		assertThat(rowOf(anaId1)).isEqualTo(before);
		ownerStillSees(anaId1, 15000, "Sueldo de Ana");
	}

	@Test
	void otherUserCannotDeleteMovementAndItStaysIntact() throws Exception {
		notFoundBody(mockMvc.perform(delete(MOVEMENTS + "/" + anaId1)
				.header("Authorization", bea.bearer())));

		assertThat(rowOf(anaId1).get("deleted_at")).isNull();
		ownerStillSees(anaId1, 15000, "Sueldo de Ana");
		mockMvc.perform(get(MOVEMENTS).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	@Test
	void bodyWithForeignUserIdOrIdDoesNotChangeOwnerOrId() throws Exception {
		String response = mockMvc.perform(post(MOVEMENTS).header("Authorization", bea.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{" + cat("INGRESO") + "\"id\":" + anaId1 + ",\"userId\":" + ana.id()
								+ ",\"amount\":700,\"type\":\"INGRESO\",\"occurredAt\":\"" + DATE + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long newId = ((Number) JsonPath.read(response, "$.id")).longValue();

		assertThat(newId).isNotIn(anaId1, anaId2, beaId);
		assertThat(rowOf(newId).get("user_id")).isEqualTo(bea.id());
		assertThat(rowOf(anaId1).get("user_id")).isEqualTo(ana.id());
		assertThat(rowOf(anaId1).get("amount")).isEqualTo(15000L);
	}

	@Test
	void updateWithForeignUserIdInBodyKeepsOwnerAndId() throws Exception {
		mockMvc.perform(put(MOVEMENTS + "/" + beaId).header("Authorization", bea.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{" + cat("GASTO") + "\"id\":" + anaId1 + ",\"userId\":" + ana.id()
								+ ",\"amount\":800,\"type\":\"GASTO\",\"occurredAt\":\"" + DATE + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(beaId))
				.andExpect(jsonPath("$.amount").value(800));

		assertThat(rowOf(beaId).get("user_id")).isEqualTo(bea.id());
		assertThat(rowOf(anaId1).get("amount")).isEqualTo(15000L);
	}

	@Test
	void deletedMovementOfOneUserDoesNotAffectTheOther() throws Exception {
		mockMvc.perform(delete(MOVEMENTS + "/" + beaId).header("Authorization", bea.bearer()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get(MOVEMENTS).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
	}

	private void ownerStillSees(long id, int amount, String description) throws Exception {
		mockMvc.perform(get(MOVEMENTS + "/" + id).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(amount))
				.andExpect(jsonPath("$.description").value(description));
	}

	private Map<String, Object> rowOf(long id) {
		return jdbcTemplate.queryForMap(
				"SELECT user_id, amount, type, occurred_at, description, deleted_at FROM movements WHERE id = ?",
				id);
	}

	private long createFor(TestUser user, int amount, String type, String description) throws Exception {
		String response = mockMvc.perform(post(MOVEMENTS).header("Authorization", user.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{" + cat(type) + "\"amount\":" + amount + ",\"type\":\"" + type + "\",\"occurredAt\":\"" + DATE
								+ "\",\"description\":\"" + description + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private String notFoundBody(ResultActions result) throws Exception {
		return result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("MOVIMIENTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
	}

	private String cat(String type) {
		return categoryField(jdbcTemplate, type);
	}
}
