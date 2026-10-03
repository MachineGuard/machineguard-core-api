package com.machineguard.platform.traceability.interfaces.rest;

/**
 * Contexto de identidad que entrega IAM. Mientras el filtro JWT de IAM no este integrado se leen de
 * cabeceras; al integrarlo basta con poblarlas desde el token validado (organizationId y userId).
 */
final class TraceabilityHeaders {

    static final String ORGANIZATION = "X-Organization-Id";
    static final String USER = "X-User-Id";

    private TraceabilityHeaders() {
    }
}
