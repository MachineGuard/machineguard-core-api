package com.machineguard.platform.acceptance;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Scope;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Estado compartido entre las clases de pasos durante un solo escenario.
 * Se registra con @Import (no con @Component) para que no lo escaneen los demás contextos de prueba.
 * Usa el scope "cucumber-glue" sin proxy: se crea una instancia nueva por escenario.
 */
@Scope("cucumber-glue")
public class ScenarioContext {

    private final JsonMapper json;

    final Map<String, UUID> organizations = new HashMap<>();
    final Map<String, UUID> users = new HashMap<>();
    final Map<String, UUID> userOrganizations = new HashMap<>();
    final Map<String, String> passwords = new HashMap<>();
    final Map<String, String> accessTokens = new HashMap<>();
    final Map<String, String> refreshTokens = new HashMap<>();
    final Map<String, String> previousRefreshTokens = new HashMap<>();
    final Map<String, UUID> zones = new HashMap<>();
    final Map<String, UUID> points = new HashMap<>();
    final Map<String, UUID> zoneOrganizations = new HashMap<>();

    MvcResult lastResult;
    UUID nonConformityId;
    UUID reportId;

    public ScenarioContext(JsonMapper json) {
        this.json = json;
    }

    int lastStatus() {
        return lastResult.getResponse().getStatus();
    }

    String lastBodyText() {
        try {
            return lastResult.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer la respuesta", e);
        }
    }

    JsonNode lastBody() {
        try {
            return json.readTree(lastBodyText());
        } catch (Exception e) {
            throw new IllegalStateException("La respuesta no es JSON válido: " + lastBodyText(), e);
        }
    }

    UUID zone(String name) {
        UUID id = zones.get(name);
        if (id == null) throw new IllegalArgumentException("Zona no definida en el escenario: " + name);
        return id;
    }

    UUID organization(String name) {
        UUID id = organizations.get(name);
        if (id == null) throw new IllegalArgumentException("Organización no definida en el escenario: " + name);
        return id;
    }

    String accessToken(String email) {
        String token = accessTokens.get(email);
        if (token == null) throw new IllegalStateException(email + " no ha iniciado sesión en el escenario");
        return token;
    }
}
