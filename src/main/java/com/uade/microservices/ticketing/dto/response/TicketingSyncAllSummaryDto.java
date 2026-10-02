package com.uade.microservices.ticketing.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resumen consolidado del proceso ETL masivo de entradas para todos los eventos del catálogo.
 */
public record TicketingSyncAllSummaryDto(
        int totalEventosProcesados,
        int eventosExitosos,
        int eventosConError,
        int totalEntradasExtraidas,
        int totalEntradasCreadas,
        int totalEntradasActualizadas,
        OffsetDateTime timestamp,
        List<TicketingSyncResultDto> resultados
) {
}
