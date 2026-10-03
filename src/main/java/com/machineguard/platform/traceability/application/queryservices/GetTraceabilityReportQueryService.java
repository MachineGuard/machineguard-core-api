package com.machineguard.platform.traceability.application.queryservices;

import com.machineguard.platform.traceability.domain.exceptions.ResourceNotFoundException;
import com.machineguard.platform.traceability.domain.model.TraceabilityReport;
import com.machineguard.platform.traceability.domain.queries.GetTraceabilityReportQuery;
import com.machineguard.platform.traceability.domain.repositories.TraceabilityReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GetTraceabilityReportQueryService {

    private final TraceabilityReportRepository reports;

    public GetTraceabilityReportQueryService(TraceabilityReportRepository reports) {
        this.reports = reports;
    }

    public TraceabilityReport handle(GetTraceabilityReportQuery query) {
        return reports.findByIdAndOrganizationId(query.reportId(), query.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reporte " + query.reportId() + " no encontrado"));
    }

    public List<TraceabilityReport> list(Long organizationId, Long monitoringZoneId) {
        return monitoringZoneId == null
                ? reports.findByOrganizationIdOrderByGeneratedAtDesc(organizationId)
                : reports.findByOrganizationIdAndMonitoringZoneIdOrderByGeneratedAtDesc(organizationId, monitoringZoneId);
    }
}
