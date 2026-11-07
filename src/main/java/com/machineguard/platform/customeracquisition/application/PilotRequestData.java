package com.machineguard.platform.customeracquisition.application;

import com.machineguard.platform.customeracquisition.domain.model.Industry;

import java.math.BigDecimal;

/** Validated pilot request fields received at the public boundary. */
public record PilotRequestData(String companyName, String contactName, String contactEmail, String contactPhone,
                               Industry industry, BigDecimal estimatedMonthlyLoss) { }
