package com.machineguard.platform.environmentalmonitoring.domain.model;

import java.util.List;

/** Technology-independent result of a paginated measurement history query. */
public record MeasurementHistoryPage(List<Measurement> items, int page, int size, long totalElements, int totalPages) {
    public MeasurementHistoryPage {
        items = List.copyOf(items);
    }
}
