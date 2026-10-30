package com.machineguard.platform.acceptance;

import io.cucumber.java.Before;
import io.cucumber.java.es.Entonces;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pasos compartidos por todas las características y limpieza de datos antes de cada escenario. */
public class CommonSteps {

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "traceability_report_excursions", "traceability_reports", "non_conformities", "excursions",
            "corrective_actions", "alerts", "incidents",
            "measurements", "thresholds", "sensor_nodes", "monitoring_points", "monitoring_zones",
            "initial_admin_invitations", "authentication_refresh_token_history", "authentication_sessions",
            "users", "organizations");

    private final ScenarioContext context;
    private final JdbcTemplate jdbc;

    public CommonSteps(ScenarioContext context, JdbcTemplate jdbc) {
        this.context = context;
        this.jdbc = jdbc;
    }

    @Before
    public void cleanDatabase() {
        TABLES_IN_DELETE_ORDER.forEach(table -> jdbc.update("DELETE FROM " + table));
    }

    @Entonces("la respuesta tiene el código {int}")
    public void responseHasStatus(int expected) {
        assertEquals(expected, context.lastStatus(), () -> "Cuerpo de la respuesta: " + context.lastBodyText());
    }

    @Entonces("la lista devuelta tiene {int} elementos")
    public void listHasSize(int expected) {
        assertEquals(200, context.lastStatus(), () -> "Cuerpo de la respuesta: " + context.lastBodyText());
        assertEquals(expected, context.lastBody().size(), () -> "Lista devuelta: " + context.lastBodyText());
    }
}
