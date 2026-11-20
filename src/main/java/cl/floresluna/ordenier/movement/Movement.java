package cl.floresluna.ordenier.movement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "movements")
public class Movement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MovementType type;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(length = 200)
	private String description;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Movement() {
	}

	public Movement(Long userId, Long amount, MovementType type, Instant occurredAt, String description) {
		this.userId = userId;
		this.amount = amount;
		this.type = type;
		// Truncada a milisegundos: lo devuelto es igual a lo guardado (plan 002).
		this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
		this.description = description;
	}

	// Reemplazo completo de los campos editables; id y dueño no cambian (R14, plan D10).
	public void replace(Long amount, MovementType type, Instant occurredAt, String description) {
		this.amount = amount;
		this.type = type;
		this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
		this.description = description;
	}

	// Borrado lógico: la fila se conserva con la fecha y hora de eliminación (R17, plan D1).
	public void markDeleted(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getAmount() {
		return amount;
	}

	public MovementType getType() {
		return type;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getDescription() {
		return description;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
