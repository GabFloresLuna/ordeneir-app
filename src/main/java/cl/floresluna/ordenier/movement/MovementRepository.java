package cl.floresluna.ordenier.movement;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementRepository extends JpaRepository<Movement, Long> {

	Optional<Movement> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

	Page<Movement> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

	Page<Movement> findByUserIdAndDeletedAtIsNullAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
			Long userId, Instant from, Instant to, Pageable pageable);
}
