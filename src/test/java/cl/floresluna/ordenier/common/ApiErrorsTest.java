package cl.floresluna.ordenier.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import cl.floresluna.ordenier.security.JwtProperties;
import cl.floresluna.ordenier.security.JwtService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ApiErrorsTest extends AbstractIntegrationTest {

	@Autowired
	private JwtProperties jwtProperties;

	private String bearer() {
		return "Bearer " + new JwtService(jwtProperties, Clock.systemUTC()).issueToken(42L);
	}

	@Test
	void unknownRouteWithGetReturns404RutaNoEncontrada() throws Exception {
		mockMvc.perform(get("/api/v1/no-existe").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("RUTA_NO_ENCONTRADA"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void unknownRouteWithPostReturns404RutaNoEncontrada() throws Exception {
		mockMvc.perform(post("/api/v1/no-existe").header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("RUTA_NO_ENCONTRADA"));
	}

	@Test
	void unsupportedMethodReturns405MetodoNoPermitido() throws Exception {
		mockMvc.perform(post("/api/v1/test/boom").header(HttpHeaders.AUTHORIZATION, bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void unsupportedContentTypeReturns415TipoContenidoNoSoportado() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.codigo").value("TIPO_CONTENIDO_NO_SOPORTADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}

	@Test
	void malformedJsonReturns400DatosInvalidos() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
	}

	@Test
	void unexpectedExceptionReturns500ErrorInternoWithoutDetails() throws Exception {
		String body = mockMvc.perform(get("/api/v1/test/boom").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(ErrorTestController.SECRET_MESSAGE)
				.doesNotContain("IllegalStateException")
				.doesNotContain("at cl.floresluna");
	}

	@Test
	void unknownRouteWithoutTokenStillReturns401NoAutenticado() throws Exception {
		mockMvc.perform(get("/api/v1/no-existe"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
	}

	@Test
	void missingRequiredParameterReturns400SolicitudInvalida() throws Exception {
		mockMvc.perform(get("/api/v1/test/needs-param").header(HttpHeaders.AUTHORIZATION, bearer()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}
}
