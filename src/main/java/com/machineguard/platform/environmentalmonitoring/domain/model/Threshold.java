package com.machineguard.platform.environmentalmonitoring.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Objects;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
import com.machineguard.platform.environmentalmonitoring.domain.exceptions.EnvironmentalMonitoringRuleException;

@Entity
@Table(name = "thresholds")
public class Threshold {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "monitoring_zone_id", nullable = false)
    private UUID monitoringZoneId;
    @Enumerated(EnumType.STRING)
    @Column(name = "environmental_variable", nullable = false, length = 20)
    private EnvironmentalVariable environmentalVariable;
    @Column(name = "minimum_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal minimumValue;
    @Column(name = "maximum_value", nullable = false, precision = 6, scale = 2)
    private BigDecimal maximumValue;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Threshold() {}

    public static Threshold configure(UUID monitoringZoneId,
            EnvironmentalVariable environmentalVariable,
            BigDecimal minimumValue,
            BigDecimal maximumValue,
            OffsetDateTime now) {
        Threshold result = new Threshold();
        result.monitoringZoneId = Objects.requireNonNull(monitoringZoneId);
        result.environmentalVariable = Objects.requireNonNull(environmentalVariable);
        result.minimumValue = NumericValue.exact(minimumValue);
        result.maximumValue = NumericValue.exact(maximumValue);
        result.createdAt = now;
        result.updatedAt = now;
        if (result.minimumValue.compareTo(result.maximumValue) >= 0) throw new EnvironmentalMonitoringRuleException("Minimum must be below maximum");
        return result;
    }

    public Threshold reconfigure(BigDecimal minimumValue, BigDecimal maximumValue, OffsetDateTime now) {
        BigDecimal minimum = NumericValue.exact(minimumValue);
        BigDecimal maximum = NumericValue.exact(maximumValue);
        if (minimum.compareTo(maximum) >= 0) throw new EnvironmentalMonitoringRuleException("Minimum must be below maximum");
        this.minimumValue = minimum;
        this.maximumValue = maximum;
        this.updatedAt = now;
        return this;
    }

    public UUID getId() { return id; }
    public UUID getMonitoringZoneId() { return monitoringZoneId; }
    public EnvironmentalVariable getEnvironmentalVariable() { return environmentalVariable; }
    public BigDecimal getMinimumValue() { return minimumValue; }
    public BigDecimal getMaximumValue() { return maximumValue; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public boolean contains(BigDecimal value) {
        return minimumValue.compareTo(value) <= 0 && maximumValue.compareTo(value) >= 0;
    }
    public boolean isOutOfRange(BigDecimal value) { return !contains(value); }
    public BigDecimal exceededBoundary(BigDecimal value) {
        return value.compareTo(minimumValue) < 0 ? minimumValue : maximumValue;
    }
    public boolean isNearLimit(BigDecimal value) {
        BigDecimal margin = maximumValue.subtract(minimumValue).multiply(new BigDecimal("0.10"));
        return contains(value) && (value.subtract(minimumValue).compareTo(margin) <= 0
                || maximumValue.subtract(value).compareTo(margin) <= 0);
    }
}
