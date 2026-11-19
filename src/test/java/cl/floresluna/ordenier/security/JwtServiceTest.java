package cl.floresluna.ordenier.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

	private final JwtProperties properties = randomProperties();
	private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(properties);
	private final JwtService service = new JwtService(properties, Clock.systemUTC());

	@Test
	void tokenHasUserIdAsSubjectAndLastsSixtyMinutes() {
		Jwt jwt = decoder.decode(service.issueToken(42L));

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
		assertThat(JwtService.EXPIRES_IN_SECONDS).isEqualTo(3600);
	}

	@Test
	void tokenIsSignedWithHs256() {
		Jwt jwt = decoder.decode(service.issueToken(1L));

		assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		String foreignToken = new JwtService(randomProperties(), Clock.systemUTC()).issueToken(1L);

		assertThatThrownBy(() -> decoder.decode(foreignToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenExpiredFiveSecondsAgoIsRejected() {
		Clock past = Clock.fixed(Instant.now().minusSeconds(3600 + 5), ZoneOffset.UTC);
		String expiredToken = new JwtService(properties, past).issueToken(1L);

		assertThatThrownBy(() -> decoder.decode(expiredToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void malformedTokenIsRejected() {
		assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(JwtException.class);
	}

	static JwtProperties randomProperties() {
		return new JwtProperties(UUID.randomUUID() + "-" + UUID.randomUUID());
	}
}
