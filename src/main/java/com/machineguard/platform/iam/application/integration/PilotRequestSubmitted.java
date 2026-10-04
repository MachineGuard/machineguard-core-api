package com.machineguard.platform.iam.application.integration;

import java.util.UUID;

/** Internal integration event contract published by Customer Acquisition. */
public record PilotRequestSubmitted(UUID requestId, String companyName, String contactName, String contactEmail) { }
