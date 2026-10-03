package com.machineguard.platform.traceability.domain.exceptions;

/** Una regla de negocio impide la operacion solicitada (se traduce a HTTP 409). */
public class DomainRuleViolationException extends RuntimeException {

    public DomainRuleViolationException(String message) {
        super(message);
    }
}
