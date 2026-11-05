package com.machineguard.platform.acceptance;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

/**
 * Suite BDD: ejecuta los escenarios Gherkin de src/test/resources/features con Cucumber.
 * Escenarios de IAM, trazabilidad y el incremento integrado de alertas/incidentes (Cap. III).
 * El reporte HTML queda en target/cucumber-reports/cucumber.html.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.machineguard.platform.acceptance")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
class CucumberAcceptanceTest {
}
