package cl.floresluna.ordenier.security;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey())
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
		return decoder;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			ApiAuthenticationEntryPoint entryPoint) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
						.permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(entryPoint))
				.exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint));
		return http.build();
	}
}
