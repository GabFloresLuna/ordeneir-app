package cl.floresluna.ordenier.budget;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

	Optional<Budget> findByUserIdAndMonth(Long userId, String month);
}
