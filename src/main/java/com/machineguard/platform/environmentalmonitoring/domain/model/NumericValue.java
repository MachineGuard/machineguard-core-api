package com.machineguard.platform.environmentalmonitoring.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringRuleException;

final class NumericValue {
    private NumericValue() {}
    static BigDecimal exact(BigDecimal value) {
        try {
            BigDecimal result = value.setScale(2, RoundingMode.UNNECESSARY);
            if (result.precision() > 6) throw new ArithmeticException();
            return result;
        } catch (ArithmeticException | NullPointerException exception) {
            throw new EnvironmentalMonitoringRuleException("Value must fit NUMERIC(6,2) without rounding");
        }
    }
}
