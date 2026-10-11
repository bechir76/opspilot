package dev.opspilot.inventory.api;

import dev.opspilot.inventory.product.InsufficientStockException;
import dev.opspilot.inventory.product.ProductNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps errors to RFC 9457 problem details. Extending {@link ResponseEntityExceptionHandler} makes
 * Spring MVC's own errors (validation, malformed JSON, ...) use the same format.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail notFound(ProductNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Product not found", e.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail insufficientStock(InsufficientStockException e) {
        return problem(HttpStatus.CONFLICT, "Insufficient stock", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
