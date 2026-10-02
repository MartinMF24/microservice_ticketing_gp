package com.uade.microservices.ticketing.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resumen de una entrada de grada sincronizada.
 */
public record EntradaSyncSummaryDto(
        UUID idEntrada,
        UUID idEvento,
        String nombreTribuna,
        BigDecimal precioUsd,
        Integer stockDisponible,
        String tipo
) {
}
