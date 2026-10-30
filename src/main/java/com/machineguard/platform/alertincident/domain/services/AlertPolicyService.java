package com.machineguard.platform.alertincident.domain.services;

import com.machineguard.platform.alertincident.domain.model.AlertSeverity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Reglas para asignar la severidad de una alerta segun cuanto se aleja la medicion del rango seguro. */
@Component
public class AlertPolicyService {

    private static final BigDecimal LOW_LIMIT = new BigDecimal("2");
    private static final BigDecimal MEDIUM_LIMIT = new BigDecimal("5");
    private static final BigDecimal HIGH_LIMIT = new BigDecimal("10");

    public AlertSeverity classify(BigDecimal measuredValue, BigDecimal minimumValue, BigDecimal maximumValue) {
        BigDecimal boundary = measuredValue.compareTo(minimumValue) < 0 ? minimumValue : maximumValue;
        BigDecimal distance = measuredValue.subtract(boundary).abs();
        if (distance.compareTo(LOW_LIMIT) < 0) {
            return AlertSeverity.LOW;
        }
        if (distance.compareTo(MEDIUM_LIMIT) < 0) {
            return AlertSeverity.MEDIUM;
        }
        if (distance.compareTo(HIGH_LIMIT) < 0) {
            return AlertSeverity.HIGH;
        }
        return AlertSeverity.CRITICAL;
    }
}
