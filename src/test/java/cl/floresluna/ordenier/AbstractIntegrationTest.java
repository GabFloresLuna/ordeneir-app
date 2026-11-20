package cl.floresluna.ordenier;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

	private static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

	private static final String TEST_JWT_SECRET = randomSecret();

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void testProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
	}

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected MockMvc mockMvc;

	@BeforeEach
	void cleanDatabase() {
		// Sin CASCADE: borraría también las categorías predefinidas (plan D3).
		jdbcTemplate.execute("TRUNCATE TABLE movements RESTART IDENTITY");
		jdbcTemplate.execute("DELETE FROM categories WHERE user_id IS NOT NULL");
		jdbcTemplate.execute("DELETE FROM users");
		jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN id RESTART");
	}

	private static String randomSecret() {
		byte[] bytes = new byte[48];
		new SecureRandom().nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}
}
