package com.machineguard.platform.environmentalmonitoring.interfaces.rest;

import com.machineguard.platform.environmentalmonitoring.application.MeasurementTimePolicy;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Codes are the machine contract; human descriptions may change independently. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=GatewayIngestionController.class)
public class GatewayIngestionExceptionHandler {
    private ProblemDetail problem(HttpStatus status,String code,String detail) {
        var result=ProblemDetail.forStatusAndDetail(status,detail);
        result.setProperty("code",code); return result;
    }
    @ExceptionHandler(MeasurementTimePolicy.InvalidCaptureTimeException.class)
    public ProblemDetail time(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST,"READING_TIME_INVALID",e.getMessage());
    }
    @ExceptionHandler(EnvironmentalMonitoringNotFoundException.class)
    public ProblemDetail missing(EnvironmentalMonitoringNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND,"NODE_NOT_AUTHORIZED",e.getMessage());
    }
    @ExceptionHandler(EnvironmentalMonitoringRuleException.class)
    public ProblemDetail rule(EnvironmentalMonitoringRuleException e) {
        String code=e.reason()==EnvironmentalMonitoringRuleException.Reason.OTHER?"INGESTION_REJECTED":e.reason().name();
        return problem(HttpStatus.CONFLICT,code,e.getMessage());
    }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ProblemDetail invalid(Exception e) {
        return problem(HttpStatus.BAD_REQUEST,"READING_INVALID","Invalid measurement or batch");
    }
}
