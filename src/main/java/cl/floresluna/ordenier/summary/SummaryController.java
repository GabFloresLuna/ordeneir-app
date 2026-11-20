package cl.floresluna.ordenier.summary;

import cl.floresluna.ordenier.security.AuthenticatedUser;
import cl.floresluna.ordenier.summary.dto.SummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resumenes")
public class SummaryController {

	private final SummaryService summaryService;
	private final AuthenticatedUser authenticatedUser;

	public SummaryController(SummaryService summaryService, AuthenticatedUser authenticatedUser) {
		this.summaryService = summaryService;
		this.authenticatedUser = authenticatedUser;
	}

	// Solo GET: Spring responde 405 al resto (plan 004 D1).
	@GetMapping("/{mes}")
	public SummaryResponse get(@PathVariable String mes) {
		return summaryService.get(authenticatedUser.id(), mes);
	}
}
