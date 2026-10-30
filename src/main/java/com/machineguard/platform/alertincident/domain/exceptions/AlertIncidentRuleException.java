package com.machineguard.platform.alertincident.domain.exceptions;

/** Una regla de negocio impide la operacion solicitada (se traduce a HTTP 409). */
public class AlertIncidentRuleException extends RuntimeException {

    public AlertIncidentRuleException(String message) {
        super(message);
    }
}
