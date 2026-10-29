package com.machineguard.platform.alertincidentmanagement.interfaces.rest;

import com.machineguard.platform.alertincidentmanagement.domain.exceptions.AlertNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = AlertController.class)
public class AlertExceptionHandler {
    @ExceptionHandler(AlertNotFoundException.class)
    public ProblemDetail notFound(AlertNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalid(MethodArgumentTypeMismatchException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid alert identifier or query parameters");
    }
}
