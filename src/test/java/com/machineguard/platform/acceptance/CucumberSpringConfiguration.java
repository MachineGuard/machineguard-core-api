package com.machineguard.platform.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Contexto Spring de la suite BDD. Usa una base H2 propia para que los escenarios
 * no compartan datos con las pruebas de integración existentes.
 */
@CucumberContextConfiguration
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@Import(ScenarioContext.class)
public class CucumberSpringConfiguration {
}
