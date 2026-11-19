package cl.floresluna.ordenier.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtPropertiesTest {

	@EnableConfigurationProperties(JwtProperties.class)
	static class PropertiesConfig {
	}

	private final ApplicationContextRunner runner =
			new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

	@Test
	void nullSecretIsRejected() {
		assertThatThrownBy(() -> new JwtProperties(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void secretOf31BytesIsRejected() {
		assertThatThrownBy(() -> new JwtProperties("a".repeat(31))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void secretOf32BytesIsAccepted() {
		assertThat(new JwtProperties("a".repeat(32)).secret()).hasSize(32);
	}

	@Test
	void secretIsMeasuredInBytesNotCharacters() {
		assertThat(new JwtProperties("ñ".repeat(16)).secret()).hasSize(16);
		assertThatThrownBy(() -> new JwtProperties("ñ".repeat(15))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void toStringDoesNotShowTheSecret() {
		assertThat(new JwtProperties("s".repeat(40)).toString()).doesNotContain("ssss");
	}

	@Test
	void startupFailsWithoutSecret() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void startupFailsWithSecretShorterThan32Bytes() {
		runner.withPropertyValues("app.jwt.secret=" + "a".repeat(31))
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void startupSucceedsWithSecretOf32Bytes() {
		runner.withPropertyValues("app.jwt.secret=" + "a".repeat(32))
				.run(context -> assertThat(context).hasNotFailed());
	}
}
