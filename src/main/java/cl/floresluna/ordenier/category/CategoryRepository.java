package cl.floresluna.ordenier.category;

import cl.floresluna.ordenier.movement.MovementType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	// Visible: no eliminada y predefinida o del usuario. Único punto de acceso a una categoría (plan D4).
	@Query("select c from Category c where c.id = :id and c.deletedAt is null "
			+ "and (c.userId is null or c.userId = :userId)")
	Optional<Category> findVisible(@Param("id") Long id, @Param("userId") Long userId);

	// Orden por tipo y nombre sin distinguir mayúsculas (R7, plan).
	@Query("select c from Category c where c.deletedAt is null and (c.userId is null or c.userId = :userId) "
			+ "order by c.type, lower(c.name), c.id")
	List<Category> findAllVisible(@Param("userId") Long userId);

	@Query("select c from Category c where c.deletedAt is null and (c.userId is null or c.userId = :userId) "
			+ "and c.type = :type order by lower(c.name), c.id")
	List<Category> findAllVisibleByType(@Param("userId") Long userId, @Param("type") MovementType type);

	// Duplicado (R6, plan D5): mismo tipo y nombre sin distinguir mayúsculas, entre predefinidas y propias.
	@Query("select count(c) > 0 from Category c where c.deletedAt is null "
			+ "and (c.userId is null or c.userId = :userId) and c.type = :type and lower(c.name) = lower(:name)")
	boolean existsVisibleByTypeAndName(@Param("userId") Long userId, @Param("type") MovementType type,
			@Param("name") String name);
}
