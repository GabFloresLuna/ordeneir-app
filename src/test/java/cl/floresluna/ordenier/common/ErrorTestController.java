package cl.floresluna.ordenier.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ErrorTestController {

	static final String SECRET_MESSAGE = "detalle-interno-que-no-debe-salir";

	@GetMapping("/api/v1/test/boom")
	String boom() {
		throw new IllegalStateException(SECRET_MESSAGE);
	}

	@GetMapping("/api/v1/test/needs-param")
	String needsParam(@RequestParam String x) {
		return x;
	}
}
