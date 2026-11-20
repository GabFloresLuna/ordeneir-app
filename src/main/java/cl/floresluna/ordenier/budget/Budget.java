package cl.floresluna.ordenier.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Presupuesto general de un mes (`AAAA-MM`) de un usuario (plan 003 D2). */
@Entity
@Table(name = "budgets")
public class Budget {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(nullable = false, updatable = false, length = 7)
	private String month;

	@Column(nullable = false)
	private Long amount;

	protected Budget() {
	}

	public Budget(Long userId, String month, Long amount) {
		this.userId = userId;
		this.month = month;
		this.amount = amount;
	}

	public void changeAmount(Long newAmount) {
		this.amount = newAmount;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getMonth() {
		return month;
	}

	public Long getAmount() {
		return amount;
	}
}
