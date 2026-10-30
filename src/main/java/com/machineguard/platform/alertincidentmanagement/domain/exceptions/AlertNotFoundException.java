package com.machineguard.platform.alertincidentmanagement.domain.exceptions;

public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String message) { super(message); }
}
