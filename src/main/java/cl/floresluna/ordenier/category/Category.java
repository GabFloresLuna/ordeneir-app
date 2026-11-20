package cl.floresluna.ordenier.category;

import cl.floresluna.ordenier.movement.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Categoría de movimientos. Sin dueño (`userId` nulo) es predefinida (plan D1). */
@Entity
@Table(name = "categories")
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", updatable = false)
	private Long userId;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 10)
	private MovementType type;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected Category() {
	}

	public Category(Long userId, String name, MovementType type) {
		this.userId = userId;
		this.name = name;
		this.type = type;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public MovementType getType() {
		return type;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
