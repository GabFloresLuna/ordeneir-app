package cl.floresluna.ordenier.movement;

import static cl.floresluna.ordenier.movement.MovementTestSupport.MOVEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.movement.MovementTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MovementReadTest extends AbstractIntegrationTest {

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = MovementTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = MovementTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void viewingOwnMovementReturns200WithItsData() throws Exception {
		long id = createFor(ana, "Almuerzo");

		view(ana, id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.amount").value(15000))
				.andExpect(jsonPath("$.type").value("GASTO"))
				.andExpect(jsonPath("$.occurredAt").value("2026-11-19T18:30:00Z"))
				.andExpect(jsonPath("$.description").value("Almuerzo"))
				.andExpect(jsonPath("$.userId").doesNotExist());
	}

	@Test
	void nonexistentDeletedAndOtherUsersMovementsReturnIdentical404() throws Exception {
		long deletedId = createFor(ana, "Eliminado");
		jdbcTemplate.update("UPDATE movements SET deleted_at = now() WHERE id = ?", deletedId);
		long othersId = createFor(bea, "Ajeno");

		String nonexistent = notFoundBody(view(ana, 999999L));
		String deleted = notFoundBody(view(ana, deletedId));
		String other = notFoundBody(view(ana, othersId));

		assertThat(deleted).isEqualTo(nonexistent);
		assertThat(other).isEqualTo(nonexistent);
		assertThat((String) JsonPath.read(nonexistent, "$.codigo")).isEqualTo("MOVIMIENTO_NO_ENCONTRADO");
	}

	@Test
	void nonNumericIdReturns400DatosInvalidos() throws Exception {
		mockMvc.perform(get(MOVEMENTS + "/abc").header("Authorization", ana.bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void viewingWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get(MOVEMENTS + "/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private long createFor(TestUser user, String description) throws Exception {
		String body = mockMvc.perform(post(MOVEMENTS).header("Authorization", user.bearer())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"amount\":15000,\"type\":\"GASTO\",\"occurredAt\":\"2026-11-19T15:30:00-03:00\","
								+ "\"description\":\"" + description + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions view(TestUser user, long id) throws Exception {
		return mockMvc.perform(get(MOVEMENTS + "/" + id).header("Authorization", user.bearer()));
	}

	private String notFoundBody(ResultActions result) throws Exception {
		return result.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("MOVIMIENTO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
	}
}
