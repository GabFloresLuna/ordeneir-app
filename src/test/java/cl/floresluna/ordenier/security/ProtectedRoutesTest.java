package cl.floresluna.ordenier.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.floresluna.ordenier.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class ProtectedRoutesTest extends AbstractIntegrationTest {

	private static final String WHOAMI = "/api/v1/test/whoami";

	@Autowired
	private JwtProperties jwtProperties;

	@Test
	void protectedRouteWithoutTokenReturns401() throws Exception {
		assertNotAuthenticated(mockMvc.perform(get(WHOAMI)));
	}

	@Test
	void unknownRouteUnderApiWithoutTokenReturns401() throws Exception {
		assertNotAuthenticated(mockMvc.perform(get("/api/v1/does-not-exist")));
	}

	@Test
	void malformedTokenReturns401() throws Exception {
		assertNotAuthenticated(mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")));
	}

	@Test
	void tokenSignedWithAnotherSecretReturns401() throws Exception {
		String token = new JwtService(JwtServiceTest.randomProperties(), Clock.systemUTC()).issueToken(42L);

		assertNotAuthenticated(mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
	}

	@Test
	void tokenExpiredFiveSecondsAgoReturns401() throws Exception {
		Clock past = Clock.fixed(Instant.now().minusSeconds(3600 + 5), ZoneOffset.UTC);
		String token = new JwtService(jwtProperties, past).issueToken(42L);

		assertNotAuthenticated(mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
	}

	@Test
	void validTokenReachesProtectedRouteWithTheUserId() throws Exception {
		String token = new JwtService(jwtProperties, Clock.systemUTC()).issueToken(42L);

		mockMvc.perform(get(WHOAMI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42));
	}

	@Test
	void registerAndLoginRoutesDoNotRequireAToken() throws Exception {
		for (String path : new String[] {"/api/v1/auth/register", "/api/v1/auth/login"}) {
			int status = mockMvc.perform(post(path)).andReturn().getResponse().getStatus();

			assertThat(status).as(path).isNotIn(401, 403);
		}
	}

	private void assertNotAuthenticated(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
				.andExpect(jsonPath("$.mensaje").isNotEmpty());
	}
}
