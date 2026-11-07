package com.machineguard.platform.customeracquisition.infrastructure.persistence;

import com.machineguard.platform.customeracquisition.domain.model.Industry;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequest;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pilot_requests")
public class PilotRequestEntity {
    @Id private UUID id;
    @Column(name = "company_name", nullable = false, length = 150) private String companyName;
    @Column(name = "contact_name", nullable = false, length = 150) private String contactName;
    @Column(name = "contact_email", nullable = false, length = 150) private String contactEmail;
    @Column(name = "contact_phone", nullable = false, length = 30) private String contactPhone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Industry industry;
    @Column(name = "estimated_monthly_loss", precision = 14, scale = 2) private BigDecimal estimatedMonthlyLoss;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PilotRequestStatus status;
    @Column(name = "pending_contact_email", unique = true, length = 150) private String pendingContactEmail;
    @Column(name = "organization_id") private UUID organizationId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "decided_at") private Instant decidedAt;

    protected PilotRequestEntity() { }

    public PilotRequestEntity(PilotRequest request) {
        id = request.id();
        companyName = request.companyName();
        contactName = request.contactName();
        contactEmail = request.contactEmail();
        contactPhone = request.contactPhone();
        industry = request.industry();
        estimatedMonthlyLoss = request.estimatedMonthlyLoss();
        status = request.status();
        pendingContactEmail = request.status() == PilotRequestStatus.PENDING ? request.contactEmail() : null;
        organizationId = request.organizationId();
        createdAt = request.createdAt();
        decidedAt = request.decidedAt();
    }

    public PilotRequest toDomain() {
        return PilotRequest.restore(id, companyName, contactName, contactEmail, contactPhone, industry,
                estimatedMonthlyLoss, status, organizationId, createdAt, decidedAt);
    }
}
