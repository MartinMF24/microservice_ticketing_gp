package com.uade.microservices.ticketing.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO con el resultado de la confirmación de reserva emitida por el servicio legado SOAP.
 */
public record ReservaEntradaResponseDto(
        String codigoConfirmacion,
        UUID idEntrada,
        UUID idEvento,
        String codigoEvento,
        String carrera,
        String nombreTribuna,
        String tipo,
        int cantidad,
        BigDecimal precioUnitarioUsd,
        BigDecimal precioTotalUsd,
        String estado,
        String mensaje,
        OffsetDateTime fechaReserva
) {
}
