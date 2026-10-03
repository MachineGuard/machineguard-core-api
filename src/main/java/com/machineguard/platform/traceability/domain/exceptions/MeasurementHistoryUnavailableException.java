package com.machineguard.platform.traceability.domain.exceptions;

/** Environmental Monitoring no entrego el historial (se traduce a HTTP 503). */
public class MeasurementHistoryUnavailableException extends RuntimeException {

    public MeasurementHistoryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
