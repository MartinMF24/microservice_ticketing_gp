package com.uade.microservices.ticketing.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resultado detallado de la sincronización ETL para un Gran Premio / evento individual.
 */
public record TicketingSyncResultDto(
        String codigoEvento,
        UUID idEvento,
        String carrera,
        Integer temporada,
        LocalDate fechaCarrera,
        int totalEntradasExtraidas,
        int entradasCreadas,
        int entradasActualizadas,
        String estado,
        String mensaje,
        OffsetDateTime timestamp,
        List<EntradaSyncSummaryDto> entradas
) {
}
