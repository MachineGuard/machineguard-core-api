package com.machineguard.platform.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machineguard.platform.iam.domain.model.OrganizationStatus;
import com.machineguard.platform.iam.domain.model.Role;
import com.machineguard.platform.iam.domain.model.SubscriptionPlan;
import com.machineguard.platform.iam.domain.model.UserStatus;
import com.machineguard.platform.iam.infrastructure.persistence.OrganizationEntity;
import com.machineguard.platform.iam.infrastructure.persistence.UserEntity;
import com.machineguard.platform.iam.infrastructure.persistence.repository.OrganizationJpaRepository;
import com.machineguard.platform.iam.infrastructure.persistence.repository.UserJpaRepository;
import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos de IAM: organizaciones, usuarios, roles, sesiones JWT y protección de la API (TS06, TS07, TS08). */
public class IamSteps {

    static final String DEFAULT_PASSWORD = "clave segura 2026";

    private final ScenarioContext context;
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationJpaRepository organizations;
    private final UserJpaRepository users;

    public IamSteps(ScenarioContext context, MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc,
                    PasswordEncoder passwordEncoder, OrganizationJpaRepository organizations,
                    UserJpaRepository users) {
        this.context = context;
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.organizations = organizations;
        this.users = users;
    }

    // ---------- Datos iniciales ----------

    @Dado("que existe la organización {string}")
    public void organizationExists(String name) {
        createOrganization(name, OrganizationStatus.ACTIVE);
    }

    @Dado("que existe la organización suspendida {string}")
    public void suspendedOrganizationExists(String name) {
        createOrganization(name, OrganizationStatus.SUSPENDED);
    }

    @Dado("que la organización {string} tiene un usuario {string} con rol {string}")
    public void organizationHasUser(String organization, String email, String role) {
        createUser(organization, email, Role.valueOf(role), UserStatus.ACTIVE);
    }

    @Dado("que la organización {string} tiene un usuario inactivo {string} con rol {string}")
    public void organizationHasInactiveUser(String organization, String email, String role) {
        createUser(organization, email, Role.valueOf(role), UserStatus.INACTIVE);
    }

    @Dado("que {string} inició sesión")
    public void userLoggedIn(String email) throws Exception {
        login(email, context.passwords.get(email));
        assertEquals(200, context.lastStatus(), () -> "No se pudo iniciar sesión: " + context.lastBodyText());
    }

    // ---------- TS07: autenticación y sesiones ----------

    @Cuando("{string} inicia sesión con su contraseña")
    public void logsInWithOwnPassword(String email) throws Exception {
        login(email, context.passwords.get(email));
    }

    @Cuando("{string} inicia sesión con la contraseña {string}")
    public void logsInWithPassword(String email, String password) throws Exception {
        login(email, password);
    }

    @Entonces("recibe un access token de tipo {string} y un refresh token")
    public void receivesTokens(String tokenType) {
        JsonNode body = context.lastBody();
        assertEquals(tokenType, body.get("tokenType").asText());
        assertFalse(body.get("accessToken").asText().isBlank());
        assertFalse(body.get("refreshToken").asText().isBlank());
    }

    @Entonces("el access token identifica al usuario {string} con rol {string}")
    public void accessTokenIdentifiesUser(String email, String role) {
        JsonNode user = context.lastBody().get("user");
        assertEquals(context.users.get(email).toString(), user.get("id").asText());
        assertEquals(context.userOrganizations.get(email).toString(), user.get("organizationId").asText());
        assertEquals(role, user.get("role").asText());
    }

    @Entonces("el refresh token de {string} no se almacena en texto plano")
    public void refreshTokenIsStoredHashed(String email) {
        String refreshToken = context.refreshTokens.get(email);
        Integer plain = jdbc.queryForObject(
                "SELECT COUNT(*) FROM authentication_sessions WHERE refresh_token_hash = ?", Integer.class, refreshToken);
        List<String> stored = jdbc.queryForList("SELECT refresh_token_hash FROM authentication_sessions", String.class);
        assertEquals(0, plain);
        assertEquals(1, stored.size());
        assertEquals(64, stored.get(0).length(), "Se espera un hash SHA-256 en hexadecimal");
    }

    @Cuando("{string} renueva su sesión")
    public void refreshesSession(String email) throws Exception {
        String current = context.refreshTokens.get(email);
        refresh(current);
        if (context.lastStatus() == 200) {
            context.previousRefreshTokens.put(email, current);
            context.refreshTokens.put(email, context.lastBody().get("refreshToken").asText());
        }
    }

    @Cuando("{string} reutiliza su refresh token anterior")
    public void reusesPreviousRefreshToken(String email) throws Exception {
        refresh(context.previousRefreshTokens.get(email));
    }

    @Entonces("el nuevo refresh token de {string} es distinto al anterior")
    public void refreshTokenWasRotated(String email) {
        assertNotEquals(context.previousRefreshTokens.get(email), context.refreshTokens.get(email));
    }

    @Cuando("{string} cierra sesión")
    public void logsOut(String email) throws Exception {
        context.lastResult = mvc.perform(bearer(post("/api/v1/auth/logout"), context.accessToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", context.refreshTokens.get(email)))))
                .andReturn();
    }

    @Cuando("{string} cambia su contraseña por {string}")
    public void changesPassword(String email, String newPassword) throws Exception {
        context.lastResult = mvc.perform(bearer(post("/api/v1/auth/password-reset"), context.accessToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "currentPassword", context.passwords.get(email), "newPassword", newPassword))))
                .andReturn();
    }

    // ---------- TS06: usuarios y roles ----------

    @Cuando("{string} registra al usuario {string} con rol {string}")
    public void registersUser(String actor, String email, String role) throws Exception {
        UUID organizationId = context.userOrganizations.get(actor);
        registerUserIn(actor, organizationId, email, role);
    }

    @Cuando("{string} intenta registrar al usuario {string} en la organización {string}")
    public void registersUserInOtherOrganization(String actor, String email, String organization) throws Exception {
        registerUserIn(actor, context.organization(organization), email, "VIEWER");
    }

    @Entonces("el usuario registrado pertenece a la organización {string}")
    public void registeredUserBelongsTo(String organization) {
        assertEquals(context.organization(organization).toString(), context.lastBody().get("organizationId").asText());
    }

    @Entonces("el usuario registrado tiene el correo {string}")
    public void registeredUserHasEmail(String email) {
        assertEquals(email, context.lastBody().get("email").asText());
    }

    @Cuando("{string} cambia el rol de {string} a {string}")
    public void changesRole(String actor, String target, String role) throws Exception {
        context.lastResult = mvc.perform(bearer(patch("/api/v1/users/" + context.users.get(target) + "/role"),
                        context.accessToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"))
                .andReturn();
    }

    @Cuando("{string} desactiva al usuario {string}")
    public void deactivatesUser(String actor, String target) throws Exception {
        context.lastResult = mvc.perform(bearer(patch("/api/v1/users/" + context.users.get(target) + "/status"),
                        context.accessToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andReturn();
    }

    @Entonces("{string} sigue siendo un administrador activo")
    public void stillActiveAdministrator(String email) {
        UserEntity user = users.findById(context.users.get(email)).orElseThrow();
        assertEquals(Role.ADMIN, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    // ---------- TS08: protección de la API y documentación ----------

    @Cuando("se consulta {string} sin token")
    public void requestWithoutToken(String path) throws Exception {
        context.lastResult = mvc.perform(get(path)).andReturn();
    }

    @Cuando("se consulta {string} con un token alterado")
    public void requestWithTamperedToken(String path) throws Exception {
        context.lastResult = mvc.perform(bearer(get(path), "eyJhbGciOiJIUzI1NiJ9.e30.firma-invalida")).andReturn();
    }

    @Cuando("{string} consulta {string}")
    public void userRequests(String email, String path) throws Exception {
        context.lastResult = mvc.perform(bearer(get(path), context.accessToken(email))).andReturn();
    }

    @Cuando("{string} consulta las excursiones indicando en las cabeceras la organización {string}")
    public void requestsExcursionsWithSpoofedHeaders(String email, String organization) throws Exception {
        context.lastResult = mvc.perform(bearer(get("/api/v1/traceability/excursions"), context.accessToken(email))
                        .header("X-Organization-Id", context.organization(organization))
                        .header("X-User-Id", UUID.randomUUID()))
                .andReturn();
    }

    @Cuando("se consulta la documentación OpenAPI")
    public void requestsOpenApi() throws Exception {
        context.lastResult = mvc.perform(get("/v3/api-docs")).andReturn();
    }

    @Entonces("la documentación declara el esquema de seguridad {string}")
    public void documentationDeclaresSecurityScheme(String scheme) {
        JsonNode schemes = context.lastBody().path("components").path("securitySchemes");
        assertTrue(schemes.has(scheme), () -> "Esquemas declarados: " + schemes);
        assertEquals("bearer", schemes.get(scheme).get("scheme").asText());
    }

    @Entonces("la documentación incluye la operación {word} {string}")
    public void documentationIncludesOperation(String method, String path) {
        JsonNode operation = context.lastBody().path("paths").path(path).path(method.toLowerCase());
        assertFalse(operation.isMissingNode(), () -> "No se encontró " + method + " " + path);
    }

    // ---------- Auxiliares ----------

    private void createOrganization(String name, OrganizationStatus status) {
        UUID id = UUID.randomUUID();
        organizations.save(new OrganizationEntity(id, name, SubscriptionPlan.FREE, status, null, Instant.now()));
        context.organizations.put(name, id);
    }

    private void createUser(String organization, String email, Role role, UserStatus status) {
        UUID id = UUID.randomUUID();
        UUID organizationId = context.organization(organization);
        users.save(new UserEntity(id, organizationId, email, passwordEncoder.encode(DEFAULT_PASSWORD),
                "Usuario " + email, role, status, Instant.now()));
        context.users.put(email, id);
        context.userOrganizations.put(email, organizationId);
        context.passwords.put(email, DEFAULT_PASSWORD);
    }

    private void login(String email, String password) throws Exception {
        context.lastResult = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        if (context.lastStatus() == 200) {
            JsonNode body = context.lastBody();
            context.accessTokens.put(email, body.get("accessToken").asText());
            context.refreshTokens.put(email, body.get("refreshToken").asText());
        }
    }

    private void refresh(String refreshToken) throws Exception {
        context.lastResult = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andReturn();
    }

    private void registerUserIn(String actor, UUID organizationId, String email, String role) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", DEFAULT_PASSWORD,
                "fullName", "Usuario registrado", "role", role));
        context.lastResult = mvc.perform(bearer(post("/api/v1/organizations/" + organizationId + "/users"),
                        context.accessToken(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
    }

    static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
