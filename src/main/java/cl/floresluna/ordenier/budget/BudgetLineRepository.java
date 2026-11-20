package cl.floresluna.ordenier.budget;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {

	// Ordenadas por nombre de categoría sin distinguir mayúsculas (plan 003, contrato de la API).
	@Query("select l from BudgetLine l, Category c where l.categoryId = c.id and l.budgetId = :budgetId "
			+ "order by lower(c.name), c.id")
	List<BudgetLine> findByBudgetIdOrderedByCategoryName(@Param("budgetId") Long budgetId);

	// Descarta las líneas de una categoría eliminada (R11, plan 003 D13).
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from BudgetLine l where l.categoryId = :categoryId")
	void deleteByCategoryId(@Param("categoryId") Long categoryId);

	// Consulta masiva: borra en el acto, antes de los INSERT nuevos (plan 003 D8).
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from BudgetLine l where l.budgetId = :budgetId")
	void deleteByBudgetId(@Param("budgetId") Long budgetId);
}
