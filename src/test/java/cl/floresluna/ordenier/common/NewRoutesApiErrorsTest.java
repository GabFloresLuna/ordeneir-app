package cl.floresluna.ordenier.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.category.CategoryTestSupport;
import cl.floresluna.ordenier.category.CategoryTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

/** R29: el formato común de errores (R20-R25 de 002) también aplica a las rutas de categorías y presupuestos. */
class NewRoutesApiErrorsTest extends AbstractIntegrationTest {

	private static final String CATEGORIES = "/api/v1/categorias";
	private static final String BUDGET = "/api/v1/presupuestos/2026-11";

	private record Route(HttpMethod method, String path) {

		@Override
		public String toString() {
			return method + " " + path;
		}
	}

	private TestUser ana;
	private String ownCategory;
	private List<Route> routesWithBody;

	@BeforeEach
	void setUp() throws Exception {
		ana = CategoryTestSupport.registerUser(mockMvc, "ana@example.com");
		ownCategory = CATEGORIES + "/" + CategoryTestSupport.insertOwn(jdbcTemplate, ana.id(), "Mi categoría", "GASTO");
		routesWithBody = List.of(
				new Route(HttpMethod.POST, CATEGORIES),
				new Route(HttpMethod.PUT, ownCategory),
				new Route(HttpMethod.PUT, BUDGET));
	}

	@Test
	void malformedJsonOnNewRoutesReturns400DatosInvalidos() throws Exception {
		for (Route route : routesWithBody) {
			MvcResult result = mockMvc.perform(build(route.method(), route.path())
					.contentType(MediaType.APPLICATION_JSON).content("{no es json")).andReturn();

			assertError(route, result, 400, "DATOS_INVALIDOS");
		}
	}

	@Test
	void textPlainOnNewRoutesReturns415TipoContenidoNoSoportado() throws Exception {
		for (Route route : routesWithBody) {
			MvcResult result = mockMvc.perform(build(route.method(), route.path())
					.contentType(MediaType.TEXT_PLAIN).content("hola")).andReturn();

			assertError(route, result, 415, "TIPO_CONTENIDO_NO_SOPORTADO");
		}
	}

	@Test
	void unsupportedMethodsOnNewRoutesReturn405MetodoNoPermitido() throws Exception {
		List<Route> unsupported = List.of(
				new Route(HttpMethod.PUT, CATEGORIES),
				new Route(HttpMethod.DELETE, CATEGORIES),
				new Route(HttpMethod.GET, ownCategory),
				new Route(HttpMethod.PATCH, ownCategory),
				new Route(HttpMethod.POST, BUDGET),
				new Route(HttpMethod.PATCH, BUDGET));
		for (Route route : unsupported) {
			MvcResult result = mockMvc.perform(build(route.method(), route.path())
					.contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

			assertError(route, result, 405, "METODO_NO_PERMITIDO");
		}
	}

	private MockHttpServletRequestBuilder build(HttpMethod method, String path) {
		return request(method, path).header(HttpHeaders.AUTHORIZATION, ana.bearer());
	}

	private void assertError(Route route, MvcResult result, int httpStatus, String code) throws Exception {
		String body = result.getResponse().getContentAsString();
		assertThat(result.getResponse().getStatus()).as(route.toString()).isEqualTo(httpStatus);
		assertThat((String) JsonPath.read(body, "$.codigo")).as(route.toString()).isEqualTo(code);
		assertThat((String) JsonPath.read(body, "$.mensaje")).as(route.toString()).isNotBlank();
	}
}
