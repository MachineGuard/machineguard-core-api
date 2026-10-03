package com.machineguard.platform.traceability.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
public class Batch {

    @Column(name = "batch_code", nullable = false, length = 80)
    private String code;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "quantity", precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "stored_at")
    private Instant storedAt;

    protected Batch() {
    }

    public Batch(String code, String productName, BigDecimal quantity, Instant storedAt) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El codigo del lote es obligatorio");
        }
        if (quantity != null && quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad del lote debe ser positiva");
        }
        this.code = code.trim();
        this.productName = productName;
        this.quantity = quantity;
        this.storedAt = storedAt;
    }

    public boolean isIdentified() {
        return code != null && !code.isBlank();
    }

    public boolean belongsToPeriod(ExcursionPeriod period) {
        return storedAt != null && period.contains(storedAt);
    }

    public String getCode() {
        return code;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getStoredAt() {
        return storedAt;
    }
}
