package cl.floresluna.ordenier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DatabaseMigrationTest extends AbstractIntegrationTest {

	@Test
	void contextStartsWithFlywayMigrationV1Applied() {
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
				Integer.class);

		assertThat(applied).isEqualTo(1);
	}

	@Test
	void usersTableRejectsTwoRowsWithTheSameEmail() {
		String insert = "INSERT INTO users (email, password_hash) VALUES (?, ?)";
		jdbcTemplate.update(insert, "a@x.com", "hash-1");

		assertThatThrownBy(() -> jdbcTemplate.update(insert, "a@x.com", "hash-2"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
