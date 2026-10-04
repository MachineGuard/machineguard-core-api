package com.machineguard.platform.iam.application.integration;

import java.util.UUID;

/** Datos del evento local que IAM procesa para una solicitud piloto. */
public record PilotRequestSubmitted(UUID requestId, String companyName, String contactName, String contactEmail) { }
