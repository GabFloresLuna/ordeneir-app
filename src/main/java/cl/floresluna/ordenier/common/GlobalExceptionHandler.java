package cl.floresluna.ordenier.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ResponseEntity<ApiError> handleEmailAlreadyRegistered() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("EMAIL_DUPLICADO", "Ya existe una cuenta con ese email."));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiError> handleInvalidCredentials() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiError("CREDENCIALES_INVALIDAS", "Email o contraseña incorrectos."));
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	ResponseEntity<ApiError> handleInvalidData() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiError("DATOS_INVALIDOS", "Los datos enviados no son válidos."));
	}
}
