package cl.floresluna.ordenier.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	public static final long EXPIRES_IN_SECONDS = 3600;

	private final JwtEncoder encoder;
	private final Clock clock;

	public JwtService(JwtProperties properties, Clock clock) {
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey()));
		this.clock = clock;
	}

	public String issueToken(long userId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(Long.toString(userId))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(EXPIRES_IN_SECONDS))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
