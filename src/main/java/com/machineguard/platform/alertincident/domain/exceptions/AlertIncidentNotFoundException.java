package com.machineguard.platform.alertincident.domain.exceptions;

public class AlertIncidentNotFoundException extends RuntimeException {

    public AlertIncidentNotFoundException(String message) {
        super(message);
    }
}
