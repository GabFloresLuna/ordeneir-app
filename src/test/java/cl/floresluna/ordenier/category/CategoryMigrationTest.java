package cl.floresluna.ordenier.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * R1 (datos), R3 y R16 (plan D16): usa su propio contenedor y Flyway por código para migrar
 * a la versión 2, cargar datos y migrar a la última, como pasaría con una base ya en uso.
 */
class CategoryMigrationTest {

	private static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void startDatabase() {
		POSTGRES.start();
		jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword()));
	}

	@AfterAll
	static void stopDatabase() {
		POSTGRES.stop();
	}

	@BeforeEach
	void startFromAnEmptySchema() {
		jdbc.execute("DROP SCHEMA public CASCADE");
		jdbc.execute("CREATE SCHEMA public");
	}

	@Test
	void migrationMovesExistingMovementsToOthersOfTheirTypeIncludingDeletedOnes() {
		migrateTo("2");
		long userId = jdbc.queryForObject(
				"INSERT INTO users (email, password_hash) VALUES ('a@x.com', 'hash') RETURNING id", Long.class);
		insertMovement(userId, "GASTO", false);
		insertMovement(userId, "INGRESO", false);
		insertMovement(userId, "GASTO", true);
		insertMovement(userId, "INGRESO", true);

		migrateToLatest();

		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT m.type AS movement_type, c.name, c.type AS category_type, c.user_id "
						+ "FROM movements m JOIN categories c ON c.id = m.category_id");
		assertThat(rows).hasSize(4);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("name")).isEqualTo("Otros");
			assertThat(row.get("category_type")).isEqualTo(row.get("movement_type"));
			assertThat(row.get("user_id")).isNull();
		});
	}

	@Test
	void migrationCreatesTheElevenPredefinedCategoriesWithOthersInBothTypes() {
		migrateToLatest();

		assertThat(count("SELECT count(*) FROM categories WHERE user_id IS NULL")).isEqualTo(11);
		assertThat(count("SELECT count(*) FROM categories WHERE user_id IS NULL AND type = 'GASTO'"))
				.isEqualTo(8);
		assertThat(count("SELECT count(*) FROM categories WHERE user_id IS NULL AND type = 'INGRESO'"))
				.isEqualTo(3);
		assertThat(count("SELECT count(*) FROM categories WHERE user_id IS NULL AND name = 'Otros' "
				+ "AND type = 'GASTO'")).isEqualTo(1);
		assertThat(count("SELECT count(*) FROM categories WHERE user_id IS NULL AND name = 'Otros' "
				+ "AND type = 'INGRESO'")).isEqualTo(1);
	}

	@Test
	void databaseRejectsACategoryWithAnInvalidType() {
		migrateToLatest();

		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO categories (user_id, name, type) VALUES (NULL, 'Rara', 'OTRO')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static void migrateTo(String version) {
		flyway().target(version).load().migrate();
	}

	private static void migrateToLatest() {
		flyway().load().migrate();
	}

	private static org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
		return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

	private static void insertMovement(long userId, String type, boolean deleted) {
		jdbc.update("INSERT INTO movements (user_id, amount, type, occurred_at, deleted_at) "
				+ "VALUES (?, 100, ?, now(), CASE WHEN ? THEN now() END)", userId, type, deleted);
	}

	private static int count(String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}
}
