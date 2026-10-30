package com.machineguard.platform.environmentalmonitoring.interfaces.rest;

import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackageClasses = EnvironmentalMonitoringController.class)
public class EnvironmentalMonitoringExceptionHandler {
    @ExceptionHandler(EnvironmentalMonitoringNotFoundException.class)
    public ProblemDetail notFound(EnvironmentalMonitoringNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
    @ExceptionHandler(EnvironmentalMonitoringRuleException.class)
    public ProblemDetail conflict(EnvironmentalMonitoringRuleException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ProblemDetail invalid(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid measurement or query parameters");
    }
}
