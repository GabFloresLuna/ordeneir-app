package cl.floresluna.ordenier.movement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovementRepository extends JpaRepository<Movement, Long> {

	Optional<Movement> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

	Page<Movement> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

	Page<Movement> findByUserIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
			Long userId, Instant from, Instant to, Pageable pageable);

	// Gasto total del mes: solo `GASTO` del usuario, no eliminados, en [from, to) (plan 003 D9, D14).
	// Devuelve nulo si no hay movimientos.
	@Query("select sum(m.amount) from Movement m where m.userId = :userId and m.type = :type "
			+ "and m.deletedAt is null and m.occurredAt >= :from and m.occurredAt < :to")
	Long sumAmount(@Param("userId") Long userId, @Param("type") MovementType type, @Param("from") Instant from,
			@Param("to") Instant to);

	// Gasto del mes agrupado por categoría (plan 003 D9).
	@Query("select new cl.floresluna.ordenier.movement.CategorySpent(m.categoryId, sum(m.amount)) "
			+ "from Movement m where m.userId = :userId and m.type = :type and m.deletedAt is null "
			+ "and m.occurredAt >= :from and m.occurredAt < :to group by m.categoryId")
	List<CategorySpent> sumAmountByCategory(@Param("userId") Long userId, @Param("type") MovementType type,
			@Param("from") Instant from, @Param("to") Instant to);
}
