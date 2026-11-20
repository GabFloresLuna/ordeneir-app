package cl.floresluna.ordenier.budget;

import cl.floresluna.ordenier.budget.dto.BudgetRequest;
import cl.floresluna.ordenier.budget.dto.BudgetResponse;
import cl.floresluna.ordenier.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos")
public class BudgetController {

	private final BudgetService budgetService;
	private final AuthenticatedUser authenticatedUser;

	public BudgetController(BudgetService budgetService, AuthenticatedUser authenticatedUser) {
		this.budgetService = budgetService;
		this.authenticatedUser = authenticatedUser;
	}

	// 200 también cuando lo crea (R17, plan).
	@PutMapping("/{mes}")
	public BudgetResponse put(@PathVariable String mes, @RequestBody BudgetRequest request) {
		return budgetService.put(authenticatedUser.id(), mes, request);
	}

	@GetMapping("/{mes}")
	public BudgetResponse get(@PathVariable String mes) {
		return budgetService.get(authenticatedUser.id(), mes);
	}

	@DeleteMapping("/{mes}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String mes) {
		budgetService.delete(authenticatedUser.id(), mes);
	}
}
