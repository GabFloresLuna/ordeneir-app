package cl.floresluna.ordenier.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Presupuesto de una categoría de gasto dentro de un presupuesto mensual (plan 003 D2). */
@Entity
@Table(name = "budget_lines")
public class BudgetLine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "budget_id", nullable = false, updatable = false)
	private Long budgetId;

	@Column(name = "category_id", nullable = false, updatable = false)
	private Long categoryId;

	@Column(nullable = false)
	private Long amount;

	protected BudgetLine() {
	}

	public BudgetLine(Long budgetId, Long categoryId, Long amount) {
		this.budgetId = budgetId;
		this.categoryId = categoryId;
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public Long getBudgetId() {
		return budgetId;
	}

	public Long getCategoryId() {
		return categoryId;
	}

	public Long getAmount() {
		return amount;
	}
}
