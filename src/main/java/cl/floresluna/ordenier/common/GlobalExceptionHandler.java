package cl.floresluna.ordenier.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ResponseEntity<ApiError> handleEmailAlreadyRegistered() {
		return json(HttpStatus.CONFLICT, "EMAIL_DUPLICADO", "Ya existe una cuenta con ese email.");
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiError> handleInvalidCredentials() {
		return json(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "Email o contraseña incorrectos.");
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class, InvalidDataException.class })
	ResponseEntity<ApiError> handleInvalidData() {
		return json(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Los datos enviados no son válidos.");
	}

	@ExceptionHandler(InvalidCategoryException.class)
	ResponseEntity<ApiError> handleInvalidCategory() {
		return json(HttpStatus.BAD_REQUEST, "CATEGORIA_INVALIDA", "La categoría no es válida.");
	}

	@ExceptionHandler(BudgetSumExceedsException.class)
	ResponseEntity<ApiError> handleBudgetSumExceeds() {
		return json(HttpStatus.BAD_REQUEST, "SUMA_CATEGORIAS_EXCEDE_GENERAL",
				"La suma de los presupuestos por categoría supera el presupuesto general.");
	}

	@ExceptionHandler(BudgetNotFoundException.class)
	ResponseEntity<ApiError> handleBudgetNotFound() {
		return json(HttpStatus.NOT_FOUND, "PRESUPUESTO_NO_ENCONTRADO", "No hay presupuesto para ese mes.");
	}

	@ExceptionHandler(CategoryDuplicateException.class)
	ResponseEntity<ApiError> handleCategoryDuplicate() {
		return json(HttpStatus.CONFLICT, "CATEGORIA_DUPLICADA", "Ya existe una categoría con ese nombre.");
	}

	@ExceptionHandler(CategoryNotFoundException.class)
	ResponseEntity<ApiError> handleCategoryNotFound() {
		return json(HttpStatus.NOT_FOUND, "CATEGORIA_NO_ENCONTRADA", "La categoría no existe.");
	}

	@ExceptionHandler(CategoryNotModifiableException.class)
	ResponseEntity<ApiError> handleCategoryNotModifiable() {
		return json(HttpStatus.FORBIDDEN, "CATEGORIA_NO_MODIFICABLE", "La categoría predefinida no se puede modificar.");
	}

	@ExceptionHandler(MovementNotFoundException.class)
	ResponseEntity<ApiError> handleMovementNotFound() {
		return json(HttpStatus.NOT_FOUND, "MOVIMIENTO_NO_ENCONTRADO", "El movimiento no existe.");
	}

	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	ResponseEntity<ApiError> handleRouteNotFound() {
		return json(HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA", "La ruta solicitada no existe.");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiError> handleMethodNotAllowed() {
		return json(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
				"El método HTTP no está permitido en esta ruta.");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiError> handleUnsupportedMediaType() {
		return json(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "TIPO_CONTENIDO_NO_SOPORTADO",
				"El tipo de contenido no está soportado.");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception exception) {
		if (exception instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
			return json(errorResponse.getStatusCode(), "SOLICITUD_INVALIDA", "La solicitud no es válida.");
		}
		log.error("Error inesperado", exception);
		return json(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error interno.");
	}

	private static ResponseEntity<ApiError> json(HttpStatusCode status, String codigo, String mensaje) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
				.body(new ApiError(codigo, mensaje));
	}
}
