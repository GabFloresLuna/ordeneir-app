package cl.floresluna.ordenier.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ResponseEntity<ApiError> handleEmailAlreadyRegistered() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("EMAIL_DUPLICADO", "Ya existe una cuenta con ese email."));
	}
}
