package cl.floresluna.ordenier.category;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	// Visible: no eliminada y predefinida o del usuario. Único punto de acceso a una categoría (plan D4).
	@Query("select c from Category c where c.id = :id and c.deletedAt is null "
			+ "and (c.userId is null or c.userId = :userId)")
	Optional<Category> findVisible(@Param("id") Long id, @Param("userId") Long userId);
}
