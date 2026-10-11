package dev.opspilot.order.api;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Makes Spring MVC's own errors (validation, malformed JSON, bad query parameters, ...) use RFC 9457
 * problem details, like the rest of the API.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
}
