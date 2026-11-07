package com.machineguard.platform.customeracquisition.interfaces.rest;

import com.machineguard.platform.customeracquisition.domain.model.Industry;
import com.machineguard.platform.customeracquisition.domain.model.PilotRequestStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PilotRequestResources {
    private PilotRequestResources() { }

    /** Field names match the Landing pilot form payload. Unknown fields such as acceptTerms are ignored. */
    public record PilotRequestSubmission(
            @NotBlank @Size(max = 150) String companyName,
            @NotBlank @Size(max = 150) String contactName,
            @NotBlank @Size(max = 150) @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$") String contactEmail,
            @NotBlank @Size(max = 30) @Pattern(regexp = "^\\+?[0-9\\s().-]{7,20}$") String contactPhone,
            @NotNull Industry industry,
            @DecimalMin("0") @DecimalMax("999999999999.99") BigDecimal estimatedMonthlyLoss) {
        public PilotRequestSubmission {
            if (companyName != null) companyName = companyName.trim();
            if (contactName != null) contactName = contactName.trim();
            if (contactEmail != null) contactEmail = contactEmail.trim().toLowerCase(java.util.Locale.ROOT);
            if (contactPhone != null) contactPhone = contactPhone.trim();
        }
    }

    public record PilotRequestAccepted(UUID id, PilotRequestStatus status) { }

    public record PilotRequestResource(UUID id, String companyName, String contactName, String contactEmail,
                                       String contactPhone, Industry industry, BigDecimal estimatedMonthlyLoss,
                                       PilotRequestStatus status, UUID organizationId, Instant createdAt, Instant decidedAt) { }

    /** invitationToken is present only when invitationIssuedNow is true; it is never stored in clear. */
    public record ApprovalResource(UUID id, PilotRequestStatus status, UUID organizationId, boolean invitationIssuedNow,
                                   String invitationToken, Instant invitationExpiresAt, String invitationAcceptPath) { }
}
