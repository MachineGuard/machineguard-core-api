package com.machineguard.platform.customeracquisition.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pilot request submitted from the Landing. It creates no tenant until an operator approves it. */
public final class PilotRequest {
    public static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    public static final Pattern PHONE = Pattern.compile("^\\+?[0-9\\s().-]{7,20}$");
    private static final BigDecimal MAX_LOSS = new BigDecimal("999999999999.99");

    private final UUID id;
    private final String companyName;
    private final String contactName;
    private final String contactEmail;
    private final String contactPhone;
    private final Industry industry;
    private final BigDecimal estimatedMonthlyLoss;
    private final Instant createdAt;
    private PilotRequestStatus status;
    private UUID organizationId;
    private Instant decidedAt;

    private PilotRequest(UUID id, String companyName, String contactName, String contactEmail, String contactPhone,
                         Industry industry, BigDecimal estimatedMonthlyLoss, PilotRequestStatus status,
                         UUID organizationId, Instant createdAt, Instant decidedAt) {
        this.id = Objects.requireNonNull(id, "pilot request id is required");
        this.companyName = requireText(companyName, "Company name", 150);
        this.contactName = requireText(contactName, "Contact name", 150);
        this.contactEmail = normalizeEmail(contactEmail);
        if (this.contactEmail.length() > 150 || !EMAIL.matcher(this.contactEmail).matches()) {
            throw new IllegalArgumentException("Contact email is not valid");
        }
        this.contactPhone = requireText(contactPhone, "Contact phone", 30);
        if (!PHONE.matcher(this.contactPhone).matches()) throw new IllegalArgumentException("Contact phone is not valid");
        this.industry = Objects.requireNonNull(industry, "industry is required");
        this.estimatedMonthlyLoss = normalizeLoss(estimatedMonthlyLoss);
        this.status = Objects.requireNonNull(status, "status is required");
        this.organizationId = organizationId;
        this.createdAt = Objects.requireNonNull(createdAt, "creation time is required");
        this.decidedAt = decidedAt;
    }

    public static PilotRequest submit(UUID id, String companyName, String contactName, String contactEmail,
                                      String contactPhone, Industry industry, BigDecimal estimatedMonthlyLoss, Instant now) {
        return new PilotRequest(id, companyName, contactName, contactEmail, contactPhone, industry,
                estimatedMonthlyLoss, PilotRequestStatus.PENDING, null, now, null);
    }

    public static PilotRequest restore(UUID id, String companyName, String contactName, String contactEmail,
                                       String contactPhone, Industry industry, BigDecimal estimatedMonthlyLoss,
                                       PilotRequestStatus status, UUID organizationId, Instant createdAt, Instant decidedAt) {
        return new PilotRequest(id, companyName, contactName, contactEmail, contactPhone, industry,
                estimatedMonthlyLoss, status, organizationId, createdAt, decidedAt);
    }

    public void approve(UUID organizationId, Instant now) {
        if (status != PilotRequestStatus.PENDING) throw new IllegalStateException("Only pending pilot requests can be approved");
        this.organizationId = Objects.requireNonNull(organizationId, "organization id is required");
        this.status = PilotRequestStatus.APPROVED;
        this.decidedAt = Objects.requireNonNull(now, "decision time is required");
    }

    public void reject(Instant now) {
        if (status != PilotRequestStatus.PENDING) throw new IllegalStateException("Only pending pilot requests can be rejected");
        this.status = PilotRequestStatus.REJECTED;
        this.decidedAt = Objects.requireNonNull(now, "decision time is required");
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "contact email is required").trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(label + " must contain 1 to " + max + " characters");
        }
        return value.trim();
    }

    private static BigDecimal normalizeLoss(BigDecimal loss) {
        if (loss == null) return null;
        if (loss.signum() < 0 || loss.compareTo(MAX_LOSS) > 0) throw new IllegalArgumentException("Estimated loss is out of range");
        return loss.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID id() { return id; }
    public String companyName() { return companyName; }
    public String contactName() { return contactName; }
    public String contactEmail() { return contactEmail; }
    public String contactPhone() { return contactPhone; }
    public Industry industry() { return industry; }
    public BigDecimal estimatedMonthlyLoss() { return estimatedMonthlyLoss; }
    public PilotRequestStatus status() { return status; }
    public UUID organizationId() { return organizationId; }
    public Instant createdAt() { return createdAt; }
    public Instant decidedAt() { return decidedAt; }
}
