package cl.floresluna.ordenier.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** R8, R9 y errores comunes: ruta sin mes, métodos no permitidos y autenticación de GET /resumenes/{mes}. */
class SummaryErrorsTest extends AbstractIntegrationTest {

	private static final String BASE = "/api/v1/resumenes";
	private static final String PATH = BASE + "/2026-11";

	private TestUser ana;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
	}

	@Test
	void summaryWithoutMonthReturns404RutaNoEncontrada() throws Exception {
		MvcResult result = mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, ana.bearer())).andReturn();

		assertError(result, 404, "RUTA_NO_ENCONTRADA");
	}

	@Test
	void postPutDeleteAndPatchOnTheRouteReturn405MetodoNoPermitido() throws Exception {
		for (HttpMethod method : new HttpMethod[] {HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE,
				HttpMethod.PATCH}) {
			MvcResult result = mockMvc.perform(request(method, PATH)
					.header(HttpHeaders.AUTHORIZATION, ana.bearer())
					.contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

			assertThat(result.getResponse().getStatus()).as(method.toString()).isEqualTo(405);
			assertError(result, 405, "METODO_NO_PERMITIDO");
		}
	}

	@Test
	void withoutTokenReturns401NoAutenticado() throws Exception {
		MvcResult result = mockMvc.perform(get(PATH)).andReturn();

		assertError(result, 401, "NO_AUTENTICADO");
	}

	@Test
	void withAnInvalidTokenReturns401NoAutenticado() throws Exception {
		MvcResult result = mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
				.andReturn();

		assertError(result, 401, "NO_AUTENTICADO");
	}

	@Test
	void invalidMonthReturnsTheCommonErrorFormat() throws Exception {
		MvcResult result = mockMvc.perform(get(BASE + "/2026-13").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
				.andReturn();

		assertError(result, 400, "DATOS_INVALIDOS");
	}

	private void assertError(MvcResult result, int httpStatus, String code) throws Exception {
		String body = result.getResponse().getContentAsString();
		assertThat(result.getResponse().getStatus()).isEqualTo(httpStatus);
		assertThat((String) JsonPath.read(body, "$.codigo")).isEqualTo(code);
		assertThat((String) JsonPath.read(body, "$.mensaje")).isNotBlank();
	}
}
