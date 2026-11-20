package cl.floresluna.ordenier.category;

import static cl.floresluna.ordenier.category.CategoryTestSupport.CATEGORIES;
import static cl.floresluna.ordenier.category.CategoryTestSupport.insertOwn;
import static cl.floresluna.ordenier.category.CategoryTestSupport.markDeleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** R1, R2, R7: lista de categorías predefinidas y propias, orden y filtro por tipo. */
class CategoryListTest extends AbstractIntegrationTest {

	private TestUser ana;
	private TestUser bea;

	@BeforeEach
	void registerUsers() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		bea = CategoryTestSupport.registerUser(mockMvc, "bea@example.com");
	}

	@Test
	void newUserSeesTheElevenPredefinedCategoriesWithOtrosInBothTypes() throws Exception {
		String body = list(ana, "");

		List<String> names = JsonPath.read(body, "$[*].name");
		List<Boolean> predefined = JsonPath.read(body, "$[*].predefined");
		List<String> gastoNames = JsonPath.read(body, "$[?(@.type == 'GASTO')].name");
		List<String> ingresoNames = JsonPath.read(body, "$[?(@.type == 'INGRESO')].name");

		assertThat(names).hasSize(11);
		assertThat(predefined).containsOnly(true);
		assertThat(gastoNames).containsExactlyInAnyOrder("Alimentación", "Transporte", "Vivienda",
				"Cuentas y servicios", "Salud", "Educación", "Entretenimiento", "Otros");
		assertThat(ingresoNames).containsExactlyInAnyOrder("Sueldo", "Honorarios", "Otros");
	}

	@Test
	void listIncludesIdNameTypeAndPredefinedFlagAndNoOwner() throws Exception {
		long ownId = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		mockMvc.perform(get(CATEGORIES).header("Authorization", ana.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + ownId + ")].name").value("Mascotas"))
				.andExpect(jsonPath("$[?(@.id == " + ownId + ")].type").value("GASTO"))
				.andExpect(jsonPath("$[?(@.id == " + ownId + ")].predefined").value(false))
				.andExpect(jsonPath("$[0].id").isNumber())
				.andExpect(jsonPath("$[0].userId").doesNotExist());
	}

	@Test
	void listMixesPredefinedAndOwnOrderedByTypeThenNameIgnoringCase() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		insertOwn(jdbcTemplate, ana.id(), "alquiler de bici", "GASTO");
		insertOwn(jdbcTemplate, ana.id(), "Bonos", "INGRESO");

		String body = list(ana, "");

		List<String> types = JsonPath.read(body, "$[*].type");
		List<String> gastoNames = JsonPath.read(body, "$[?(@.type == 'GASTO')].name");
		List<String> ingresoNames = JsonPath.read(body, "$[?(@.type == 'INGRESO')].name");
		assertThat(types).hasSize(14).isSortedAccordingTo(Comparator.naturalOrder());
		assertThat(gastoNames).containsExactly("Alimentación", "alquiler de bici", "Cuentas y servicios",
				"Educación", "Entretenimiento", "Mascotas", "Otros", "Salud", "Transporte", "Vivienda");
		assertThat(ingresoNames).containsExactly("Bonos", "Honorarios", "Otros", "Sueldo");
	}

	@Test
	void ownCategoriesAreVisibleOnlyToTheirOwner() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");

		assertThat((List<String>) JsonPath.read(list(ana, ""), "$[*].name")).contains("Mascotas");
		String beaBody = list(bea, "");
		assertThat((List<String>) JsonPath.read(beaBody, "$[*].name")).doesNotContain("Mascotas").hasSize(11);
	}

	@Test
	void deletedOwnCategoriesAreNotListed() throws Exception {
		long id = insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		markDeleted(jdbcTemplate, id);

		assertThat((List<String>) JsonPath.read(list(ana, ""), "$[*].name")).doesNotContain("Mascotas").hasSize(11);
	}

	@Test
	void tipoFilterReturnsOnlyThatType() throws Exception {
		insertOwn(jdbcTemplate, ana.id(), "Mascotas", "GASTO");
		insertOwn(jdbcTemplate, ana.id(), "Bonos", "INGRESO");

		String gasto = list(ana, "?tipo=GASTO");
		String ingreso = list(ana, "?tipo=INGRESO");

		assertThat((List<String>) JsonPath.read(gasto, "$[*].type")).hasSize(9).containsOnly("GASTO");
		assertThat((List<String>) JsonPath.read(ingreso, "$[*].name")).containsExactly("Bonos", "Honorarios",
				"Otros", "Sueldo");
	}

	@Test
	void invalidTipoReturns400DatosInvalidos() throws Exception {
		for (String tipo : List.of("OTRO", "gasto", "")) {
			mockMvc.perform(get(CATEGORIES + "?tipo=" + tipo).header("Authorization", ana.bearer()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
					.andExpect(jsonPath("$.mensaje").isNotEmpty());
		}
	}

	@Test
	void listWithoutTokenReturns401() throws Exception {
		mockMvc.perform(get(CATEGORIES))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	private String list(TestUser user, String query) throws Exception {
		return mockMvc.perform(get(CATEGORIES + query).header("Authorization", user.bearer()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}
}
