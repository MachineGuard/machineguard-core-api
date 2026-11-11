package com.machineguard.platform.alertincident.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Resources / DTOs de la Interface Layer de Alert and Incident Management. */
public final class AlertIncidentResources {

    private AlertIncidentResources() {
    }

    // ---- Solicitudes ----

    public record AcknowledgeAlertResource(@Size(max = 500) String note) {
    }

    public record RegisterCorrectiveActionResource(@NotBlank @Size(max = 1000) String description,
                                                   @Size(max = 1000) String notes) {
    }

    public record ResolveIncidentResource(@Size(max = 1000) String notes) {
    }

    // ---- Respuestas ----

    public record IncidentResource(UUID id, UUID originMeasurementId, UUID monitoringZoneId, String environmentalVariable,
                                   String status, Instant openedAt, Instant resolvedAt, UUID resolvedBy,
                                   String resolutionNotes, boolean historical) {
    }

    public record AlertResource(UUID id, UUID incidentId, UUID monitoringZoneId, String severity, String status,
                                String message, Instant raisedAt, Instant acknowledgedAt, Instant escalatedAt,boolean historical,
                                String environmentalVariable, BigDecimal measuredValue, BigDecimal minimumValue,
                                BigDecimal maximumValue) {
    }

    public record AcknowledgementResource(UUID acknowledgedBy, Instant acknowledgedAt, String note) {
    }

    public record CorrectiveActionResource(UUID id, UUID incidentId, String description, UUID performedBy,
                                           Instant performedAt, String notes) {
    }

    /** alert y acknowledgement pueden ser nulos: el incidente aun no tiene alerta o nadie la reconocio. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IncidentDetailResource(IncidentResource incident, AlertResource alert,
                                         AcknowledgementResource acknowledgement,
                                         List<CorrectiveActionResource> correctiveActions,
                                         boolean withinSafeRange, boolean resolutionAllowed,List<String> resolutionBlockReasons) {
    }

    /** Resultado del reconocimiento: la alerta y el incidente quedan en su nuevo estado. */
    public record AlertAcknowledgementResource(UUID alertId, UUID incidentId, UUID acknowledgedBy,
                                               Instant acknowledgedAt, String note, String alertStatus,
                                               String incidentStatus) {
    }
}
