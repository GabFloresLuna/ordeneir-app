package cl.floresluna.ordenier.movement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import cl.floresluna.ordenier.AbstractIntegrationTest;

class MovementMigrationTest extends AbstractIntegrationTest {

	private static final String INSERT =
			"INSERT INTO movements (user_id, amount, type, occurred_at) VALUES (?, ?, ?, now())";

	@Autowired
	private MovementRepository movementRepository;

	@Test
	void contextStartsWithValidateAndMigrationV2Applied() {
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success = true",
				Integer.class);

		assertThat(applied).isEqualTo(1);
	}

	@Test
	void databaseRejectsAmountZero() {
		long userId = insertUser("a@x.com");

		assertThatThrownBy(() -> jdbcTemplate.update(INSERT, userId, 0L, "GASTO"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsAmountAboveTheMaximum() {
		long userId = insertUser("a@x.com");

		assertThatThrownBy(() -> jdbcTemplate.update(INSERT, userId, 1_000_000_000_001L, "GASTO"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseAcceptsTheAmountLimitsOneAndOneTrillion() {
		long userId = insertUser("a@x.com");

		jdbcTemplate.update(INSERT, userId, 1L, "GASTO");
		jdbcTemplate.update(INSERT, userId, 1_000_000_000_000L, "INGRESO");

		Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM movements", Integer.class);
		assertThat(count).isEqualTo(2);
	}

	@Test
	void databaseRejectsAnInvalidType() {
		long userId = insertUser("a@x.com");

		assertThatThrownBy(() -> jdbcTemplate.update(INSERT, userId, 100L, "OTRO"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsAMovementOfANonexistentUser() {
		assertThatThrownBy(() -> jdbcTemplate.update(INSERT, 999_999L, 100L, "GASTO"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void movementIsSavedAndReadWithItsDateInMilliseconds() {
		long userId = insertUser("a@x.com");
		Instant occurredAt = Instant.parse("2026-11-19T18:30:00.123Z");

		Movement saved = movementRepository.saveAndFlush(
				new Movement(userId, 15_000L, MovementType.GASTO, occurredAt, "Almuerzo"));
		Movement read = movementRepository.findById(saved.getId()).orElseThrow();

		assertThat(read.getUserId()).isEqualTo(userId);
		assertThat(read.getAmount()).isEqualTo(15_000L);
		assertThat(read.getType()).isEqualTo(MovementType.GASTO);
		assertThat(read.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(read.getDescription()).isEqualTo("Almuerzo");
		assertThat(read.getDeletedAt()).isNull();
	}

	@Test
	void movementTruncatesTheDateToMilliseconds() {
		Movement movement = new Movement(1L, 100L, MovementType.INGRESO,
				Instant.parse("2026-11-19T18:30:00.123456789Z"), null);

		assertThat(movement.getOccurredAt()).isEqualTo(Instant.parse("2026-11-19T18:30:00.123Z"));
	}

	@Test
	void finderReturnsOnlyTheOwnersNotDeletedMovement() {
		long ownerId = insertUser("a@x.com");
		long otherId = insertUser("b@x.com");
		Movement own = movementRepository.save(
				new Movement(ownerId, 100L, MovementType.INGRESO, Instant.now(), null));
		Movement deleted = movementRepository.save(
				new Movement(ownerId, 200L, MovementType.GASTO, Instant.now(), null));
		jdbcTemplate.update("UPDATE movements SET deleted_at = now() WHERE id = ?", deleted.getId());

		assertThat(movementRepository.findByIdAndUserIdAndDeletedAtIsNull(own.getId(), ownerId)).isPresent();
		assertThat(movementRepository.findByIdAndUserIdAndDeletedAtIsNull(own.getId(), otherId)).isEmpty();
		assertThat(movementRepository.findByIdAndUserIdAndDeletedAtIsNull(deleted.getId(), ownerId)).isEmpty();
	}

	private long insertUser(String email) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO users (email, password_hash) VALUES (?, 'hash') RETURNING id",
				Long.class, email);
	}
}
